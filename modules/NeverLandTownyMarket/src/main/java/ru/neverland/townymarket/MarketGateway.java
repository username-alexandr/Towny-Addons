package ru.neverland.townymarket;
import java.util.*;
import java.lang.reflect.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.object.economy.*;
import ru.neverland.integration.*;
import static ru.neverland.townymarket.MarketData.*;
public final class MarketGateway implements MarketPayments.Gateway {
    private final JavaPlugin plugin;private final MarketJournal repo;
    public MarketGateway(JavaPlugin plugin,MarketJournal repo){this.plugin=plugin;this.repo=repo;repo.audit(this::auditRecord);}
    public ru.neverland.core.AuditRecord auditRecord(Order o){try(var guard=ru.neverland.core.SuiteState.guard()){var l=repo.listing(o.lot());if(l==null)throw new IllegalStateException("Предложение не найдено для журнала");return new ru.neverland.core.AuditRecord(ru.neverland.core.AuditRecord.id(plugin.getName(),o.id().toString(),o.auditStep()),o.phaseAt(),plugin.getName(),"MARKET_DEAL",o.auditOutcome(),o.id().toString(),ru.neverland.core.AuditTrail.player(o.actor()),ru.neverland.core.AuditTrail.town(o.seller()),o.city()?ru.neverland.core.AuditTrail.town(o.buyer()):ru.neverland.core.AuditTrail.player(o.buyer()),ru.neverland.core.AuditTrail.item(MarketCatalog.decode(l.item())),o.amount(),ru.neverland.core.AuditTrail.cents(o.total()),"lot="+o.lot()+"; phase="+o.auditStep()+"; unit="+ru.neverland.core.AuditTrail.cents(o.unit())+"; "+o.note());}}
    public Town town(UUID id){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return TownyAPI.getInstance().getTown(id);} }
    public Town town(Player p){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {var r=TownyAPI.getInstance().getResident(p);return r==null?null:r.getTownOrNull();} }
    public String name(UUID id){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {var t=town(id);return t==null?"Удалённый город":t.getName();} }
    private ru.neverland.core.ApiServices.Connection storage(){return ru.neverland.core.ApiServices.require("NeverLandTownyBuilds","ru.neverland.townybuilds.api.MarketStorageApi","level","open","snapshot","reserve","deliver","refund","close","acknowledge","forget","claim","resolveClaim");}
    public Object call(String method,Class<?>[] types,Object... values)throws Exception { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {try{return storage().invoke(method,types,values);}catch(InvocationTargetException ex){if(ex.getCause() instanceof Exception e)throw e;throw new IllegalStateException(ex.getCause());}} }
    public boolean available(){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {try{storage();return true;}catch(Exception ex){return false;}} }
    public int level(UUID id,String project){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {if(id==null)return 0;try{return ((Number)call("level",new Class<?>[]{UUID.class,String.class},id,project)).intValue();}catch(Exception ex){return 0;}} }
    @SuppressWarnings("unchecked") public Map<String,Object> snapshot(Listing l)throws Exception{ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {return (Map<String,Object>)call("snapshot",new Class<?>[]{UUID.class,UUID.class},l.town(),l.id());} }
    public int stock(Listing l)throws Exception { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {var data=snapshot(l);return data.get("available") instanceof Number n&&Boolean.TRUE.equals(data.get("open"))?n.intValue():0;} }
    public String sellerReady(Listing l){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {if(town(l.town())==null)return "Город-продавец удалён";if(!available())return "Нужен Builds 0.8.8 или новее";
        int min=l.scope()==Scope.LOCAL?1:Math.max(1,Math.min(5,plugin.getConfig().getInt("buildings.global-market-level",2)));
        if(level(l.town(),"market")<min)return "Необходим действующий рынок уровня "+min;
        if(l.scope()==Scope.GLOBAL&&level(l.town(),"merchant_guild")<1)return "Для экспорта нужна действующая гильдия торговцев";return null;
    } }
    public String imports(UUID sellerId,UUID buyerId){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {Town seller=town(sellerId),buyer=town(buyerId);if(seller==null||buyer==null)return "Один из городов удалён";
        var nation=buyer.getNationOrNull();boolean same=nation!=null&&seller.getNationOrNull()!=null&&nation.getUUID().equals(seller.getNationOrNull().getUUID());
        if(!PoliciesAccess.importsAllowed(buyerId,sellerId,same))return "Импорт ограничен политикой покупателя";
        if(ru.neverland.core.DiplomacyAccess.tradeBlocked(sellerId,buyerId))return "Торговля закрыта дипломатией или реестр недоступен";
        var taxes=Bukkit.getPluginManager().getPlugin("NeverLandTownyTaxes");if(taxes==null)taxes=Bukkit.getPluginManager().getPlugin("TaxyTowny");
        if(taxes!=null){if(!taxes.isEnabled())return "Система санкций недоступна";try{var result=taxes.getName().equals("NeverLandTownyTaxes")?ru.neverland.core.ApiServices.call("NeverLandTownyTaxes","ru.neverland.townytaxes.api.NeverLandTownyTaxesApi","isTradeBlocked",new Class<?>[]{UUID.class,UUID.class},sellerId,buyerId):taxes.getClass().getMethod("isTradeBlocked",UUID.class,UUID.class).invoke(taxes,sellerId,buyerId);if(!(result instanceof Boolean b))return "Система санкций недоступна";if(b)return "Торговля запрещена санкциями";}catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return "Система санкций недоступна";}}
        return null;
    } }
    @Override public String ready(Order o){ try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {var l=repo.listing(o.lot());if(l==null||l.state()!=ListingState.ACTIVE)return "Предложение закрыто";if(!plugin.getConfig().getBoolean("enabled",true))return "Новые покупки отключены";
        String blocked=sellerReady(l);if(blocked!=null)return blocked;if(!TownyEconomyHandler.isActive()||TownyEconomyHandler.activeAdapter()==null)return "Дождитесь подключения экономики Towny";
        if(o.city()){if(level(o.buyer(),"market")<1)return "В городе-покупателе нужен действующий рынок";blocked=imports(o.seller(),o.buyer());if(blocked!=null)return blocked;
            try{return TreasuryAccess.canSpend(town(o.buyer()),"infrastructure",o.total()/100.0)?null:"Недостаточно денег в бюджете инфраструктуры";}catch(RuntimeException ex){return "Бюджет недоступен";}}
        var resident=TownyAPI.getInstance().getResident(o.buyer());if(resident==null||resident.getTownOrNull()==null||!resident.getTownOrNull().getUUID().equals(o.seller()))return "Городской рынок доступен жителям этого города";
        return resident.getAccount().canPayFromHoldings(o.total()/100.0)?null:"Недостаточно личных средств";
    } }
    @Override public String reserve(Order o)throws Exception{ try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {return (String)call("reserve",new Class<?>[]{UUID.class,UUID.class,UUID.class,UUID.class,boolean.class,int.class},o.seller(),o.lot(),o.id(),o.buyer(),o.city(),o.amount());} }
    @Override public String deliver(Order o)throws Exception{ try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {return (String)call("deliver",new Class<?>[]{UUID.class,UUID.class,UUID.class},o.seller(),o.lot(),o.id());} }
    @Override public String refund(Order o)throws Exception{ try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {return (String)call("refund",new Class<?>[]{UUID.class,UUID.class,UUID.class},o.seller(),o.lot(),o.id());} }
    @Override public void acknowledge(Order o)throws Exception{ try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {call("acknowledge",new Class<?>[]{UUID.class,UUID.class,UUID.class},o.seller(),o.lot(),o.id());} }
    @Override public String receipt(Order o)throws Exception { try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {var l=repo.listing(o.lot());if(l==null)return "MISSING";Object holds=snapshot(l).get("holds");return holds instanceof Map<?,?> m&&m.get(o.id().toString()) instanceof String value?value:"MISSING";} }
    @Override public boolean debit(Order o){throw new IllegalStateException("Покупке нужна подтверждённая асинхронная оплата");}
    @Override public boolean credit(Order o){throw new IllegalStateException("Покупке нужна подтверждённая асинхронная выплата");}
    public java.util.function.Supplier<Boolean> preparePayment(Order o,boolean incoming){try(var guard=ru.neverland.core.SuiteState.guard()){
        Town city=incoming?town(o.seller()):o.city()?town(o.buyer()):null;var resident=incoming||o.city()?null:TownyAPI.getInstance().getResident(o.buyer());
        if(city==null&&resident==null||!TownyEconomyHandler.isActive()||TownyEconomyHandler.activeAdapter()==null)return ()->false;
        Account account=city==null?resident.getAccount():city.getAccount();String token="[market:"+o.id()+"]";double amount=o.total()/100.0;
        return ()->{if(!TownyEconomyHandler.isActive()||TownyEconomyHandler.activeAdapter()==null)return false;var currentCity=city==null?null:TownyAPI.getInstance().getTown(city.getUUID());var currentResident=resident==null?null:TownyAPI.getInstance().getResident(o.buyer());if(city!=null&&(currentCity==null||currentCity.getAccount()!=account)||resident!=null&&(currentResident==null||currentResident.getAccount()!=account))return false;
            if(!incoming&&o.city()&&Bukkit.getPluginManager().getPlugin("NeverLandTownyTreasuryPlus")!=null)throw new IllegalStateException("Казна требует бюджетную квитанцию покупки");
            var evidence=new ru.neverland.core.PaymentEvidence(account,incoming,token,o.total());var observer=new AccountObserver(){public void withdrew(Account source,double value,String note){evidence.observe(source,false,value,note);}public void deposited(Account source,double value,String note){evidence.observe(source,true,value,note);}};
            synchronized(account){account.addObserver(observer);try{try{String reason=token+" Покупка на рынке";boolean result=incoming?account.deposit(amount,"[NLT|income|infrastructure|"+(o.city()?"trade":"shop")+"] "+reason):account.withdraw(amount,reason);if(evidence.observed())return true;throw new IllegalStateException("Банк вернул "+result+" без точной квитанции; требуется сверка "+token);}catch(RuntimeException error){if(evidence.observed())return true;throw error;}}finally{account.removeObserver(observer);}}
        };
    }}
}
