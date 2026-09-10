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
    private final JavaPlugin plugin;private final MarketRepository repo;
    public MarketGateway(JavaPlugin plugin,MarketRepository repo){this.plugin=plugin;this.repo=repo;}
    public Town town(UUID id){return TownyAPI.getInstance().getTown(id);}
    public Town town(Player p){var r=TownyAPI.getInstance().getResident(p);return r==null?null:r.getTownOrNull();}
    public String name(UUID id){var t=town(id);return t==null?"Удалённый город":t.getName();}
    private Object storage()throws Exception {var p=Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds");if(p==null||!p.isEnabled())throw new IllegalStateException("Нужен Builds 0.8.8 или новее");var api=Class.forName("ru.neverland.townybuilds.api.MarketStorageApi",true,p.getClass().getClassLoader());var service=Bukkit.getServicesManager().load(api);if(service==null)throw new IllegalStateException("Склад рынка недоступен");return service;}
    public Object call(String method,Class<?>[] types,Object... values)throws Exception {var target=storage();try{return target.getClass().getMethod(method,types).invoke(target,values);}catch(InvocationTargetException ex){if(ex.getCause() instanceof Exception e)throw e;throw new IllegalStateException(ex.getCause());}}
    public boolean available(){try{storage();return true;}catch(Exception ex){return false;}}
    public int level(UUID id,String project){if(id==null)return 0;try{return ((Number)call("level",new Class<?>[]{UUID.class,String.class},id,project)).intValue();}catch(Exception ex){return 0;}}
    @SuppressWarnings("unchecked") public Map<String,Object> snapshot(Listing l)throws Exception{return (Map<String,Object>)call("snapshot",new Class<?>[]{UUID.class,UUID.class},l.town(),l.id());}
    public int stock(Listing l)throws Exception {var data=snapshot(l);return data.get("available") instanceof Number n&&Boolean.TRUE.equals(data.get("open"))?n.intValue():0;}
    public String sellerReady(Listing l){if(town(l.town())==null)return "Город-продавец удалён";if(!available())return "Нужен Builds 0.8.8 или новее";
        int min=l.scope()==Scope.LOCAL?1:Math.max(1,Math.min(5,plugin.getConfig().getInt("buildings.global-market-level",2)));
        if(level(l.town(),"market")<min)return "Необходим действующий рынок уровня "+min;
        if(l.scope()==Scope.GLOBAL&&level(l.town(),"merchant_guild")<1)return "Для экспорта нужна действующая гильдия торговцев";return null;
    }
    public String imports(UUID sellerId,UUID buyerId){Town seller=town(sellerId),buyer=town(buyerId);if(seller==null||buyer==null)return "Один из городов удалён";
        var nation=buyer.getNationOrNull();boolean same=nation!=null&&seller.getNationOrNull()!=null&&nation.getUUID().equals(seller.getNationOrNull().getUUID());
        if(!PoliciesAccess.importsAllowed(buyerId,sellerId,same))return "Импорт ограничен политикой покупателя";
        var taxes=Bukkit.getPluginManager().getPlugin("NeverLandTownyTaxes");if(taxes==null)taxes=Bukkit.getPluginManager().getPlugin("TaxyTowny");
        if(taxes!=null){if(!taxes.isEnabled())return "Система санкций недоступна";try{var result=taxes.getClass().getMethod("isTradeBlocked",UUID.class,UUID.class).invoke(taxes,sellerId,buyerId);if(!(result instanceof Boolean b))return "Система санкций недоступна";if(b)return "Торговля запрещена санкциями";}catch(ReflectiveOperationException ex){return "Система санкций недоступна";}}
        return null;
    }
    @Override public String ready(Order o){var l=repo.listing(o.lot());if(l==null||l.state()!=ListingState.ACTIVE)return "Предложение закрыто";if(!plugin.getConfig().getBoolean("enabled",true))return "Новые покупки отключены";
        String blocked=sellerReady(l);if(blocked!=null)return blocked;if(!TownyEconomyHandler.isActive())return "Экономика Towny отключена";
        if(o.city()){if(level(o.buyer(),"market")<1)return "В городе-покупателе нужен действующий рынок";blocked=imports(o.seller(),o.buyer());if(blocked!=null)return blocked;
            try{return TreasuryAccess.canSpend(town(o.buyer()),"infrastructure",o.total()/100.0)?null:"Недостаточно денег в бюджете инфраструктуры";}catch(RuntimeException ex){return "Бюджет недоступен";}}
        var resident=TownyAPI.getInstance().getResident(o.buyer());if(resident==null||resident.getTownOrNull()==null||!resident.getTownOrNull().getUUID().equals(o.seller()))return "Городской рынок доступен жителям этого города";
        return resident.getAccount().canPayFromHoldings(o.total()/100.0)?null:"Недостаточно личных средств";
    }
    @Override public String reserve(Order o)throws Exception{return (String)call("reserve",new Class<?>[]{UUID.class,UUID.class,UUID.class,UUID.class,boolean.class,int.class},o.seller(),o.lot(),o.id(),o.buyer(),o.city(),o.amount());}
    @Override public String deliver(Order o)throws Exception{return (String)call("deliver",new Class<?>[]{UUID.class,UUID.class,UUID.class},o.seller(),o.lot(),o.id());}
    @Override public String refund(Order o)throws Exception{return (String)call("refund",new Class<?>[]{UUID.class,UUID.class,UUID.class},o.seller(),o.lot(),o.id());}
    @Override public void acknowledge(Order o)throws Exception{call("acknowledge",new Class<?>[]{UUID.class,UUID.class,UUID.class},o.seller(),o.lot(),o.id());}
    @Override public String receipt(Order o)throws Exception {var l=repo.listing(o.lot());if(l==null)return "MISSING";Object holds=snapshot(l).get("holds");return holds instanceof Map<?,?> m&&m.get(o.id().toString()) instanceof String value?value:"MISSING";}
    @Override public boolean debit(Order o)throws Exception{return payment(o,false);}@Override public boolean credit(Order o)throws Exception{return payment(o,true);}
    private boolean payment(Order o,boolean incoming)throws Exception{
        Town city=incoming?town(o.seller()):o.city()?town(o.buyer()):null;var resident=incoming||o.city()?null:TownyAPI.getInstance().getResident(o.buyer());
        if(city==null&&resident==null)return false;Account account=city==null?resident.getAccount():city.getAccount();String token="[market:"+o.id()+"]";var evidence=new PaymentEvidence(account,incoming,token,o.total());
        var observer=new AccountObserver(){public void withdrew(Account source,double amount,String reason){evidence.observe(source,false,amount,reason);}public void deposited(Account source,double amount,String reason){evidence.observe(source,true,amount,reason);}};
        synchronized(account){account.addObserver(observer);try{String reason=token+" Покупка на рынке";double amount=o.total()/100.0;boolean result;
            try{result=incoming?TreasuryAccess.deposit(city,"infrastructure",o.city()?"trade":"shop",false,amount,reason):o.city()?TreasuryAccess.withdraw(city,"infrastructure","trade",amount,reason):account.withdraw(amount,reason);}
            catch(RuntimeException ex){if(evidence.observed())return true;throw ex;}return evidence.result(result);
        }finally{account.removeObserver(observer);}}
    }
}
