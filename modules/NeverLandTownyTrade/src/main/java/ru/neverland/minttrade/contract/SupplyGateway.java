package ru.neverland.minttrade.contract;
import java.util.*;
import java.lang.reflect.InvocationTargetException;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.object.economy.*;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.minttrade.integration.*;
import ru.neverland.minttrade.service.TradeService;
import ru.neverland.integration.TreasuryAccess;
/** Only this adapter touches Towny accounts or the optional cross-plugin API. */
public final class SupplyGateway implements SupplyProcessor.Gateway {
    private final JavaPlugin plugin;private final TownyHook towny;private final TradeService trade;private final TaxesBridge taxes;
    public SupplyGateway(JavaPlugin plugin,TownyHook towny,TradeService trade,TaxesBridge taxes){this.plugin=plugin;this.towny=towny;this.trade=trade;this.taxes=taxes;}
    public boolean available(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {try{access();return true;}catch(Exception ex){return false;}} }
    private ru.neverland.core.ApiServices.Connection access(){return ru.neverland.core.ApiServices.require(plugin.getConfig().getString("warehouse.plugin","NeverLandTownyBuilds"),"ru.neverland.townybuilds.api.BuildingStorageApi","reserveTrade","settleTrade","acknowledgeTrade");}
    private Object call(String method,Class<?>[] signature,Object... args)throws Exception {
        try{return access().invoke(method,signature,args);}
        catch(InvocationTargetException ex){if(ex.getCause() instanceof Exception e)throw e;throw new IllegalStateException(ex.getCause());}
    }
    public String termsReady(SupplyContract c){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        Town seller=towny.town(c.terms().seller()),buyer=towny.town(c.terms().buyer());
        if(seller==null||buyer==null)return "Один из городов удалён";
        if(!available())return "Для автопоставок нужен NeverLandTownyBuilds 0.8.7 или новее";
        if(trade.marketLevel(seller)<1||trade.marketLevel(buyer)<1)return "В обоих городах нужен действующий рынок";
        if(!TradeService.importsAllowed(buyer,seller))return "Импорт ограничен политикой покупателя";
        return taxes.supplyRestriction(seller.getUUID(),buyer.getUUID());
    } }
    @Override public String ready(SupplyContract c){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        if(!plugin.getConfig().getBoolean("contracts.enabled",true))return "Автопоставки отключены администратором";
        String blocked=termsReady(c);if(blocked!=null)return blocked;
        Town buyer=towny.town(c.terms().buyer());
        if(!TownyEconomyHandler.isActive())return "Экономика Towny отключена";
        try{if(!TreasuryAccess.canSpend(buyer,"infrastructure",c.terms().cents()/100.0))return "Не хватает денег в казне или бюджете инфраструктуры";}
        catch(RuntimeException ex){return "Бюджет инфраструктуры недоступен";}
        return null;
    } }
    public static ItemStack decode(String data){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));} }
    @Override public String reserve(SupplyContract c)throws Exception { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {return (String)call("reserveTrade",new Class<?>[]{UUID.class,UUID.class,UUID.class,ItemStack.class,int.class},c.terms().seller(),c.terms().buyer(),c.attempt().id(),decode(c.terms().itemData()),c.terms().amount());} }
    @Override public String settle(SupplyContract c,boolean deliver)throws Exception { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {return (String)call("settleTrade",new Class<?>[]{UUID.class,UUID.class,boolean.class},c.terms().seller(),c.attempt().id(),deliver);} }
    public ru.neverland.core.AuditRecord auditRecord(SupplyContract c,UUID actor,String step,long at,boolean agreement){try(var guard=ru.neverland.core.SuiteState.guard()){var t=c.terms();String operation=agreement?t.id().toString():c.attempt().id().toString();return new ru.neverland.core.AuditRecord(ru.neverland.core.AuditRecord.id(plugin.getName(),operation,agreement?step:c.attempt().auditStep()),agreement?at:c.attempt().transitionedAt(),plugin.getName(),agreement?"SUPPLY_AGREEMENT":"SUPPLY_DEAL",agreement?step.split(":",2)[0]:c.attempt().auditOutcome(),operation,actor==null?ru.neverland.core.AuditRecord.Party.system():ru.neverland.core.AuditTrail.player(actor),ru.neverland.core.AuditTrail.town(t.seller()),ru.neverland.core.AuditTrail.town(t.buyer()),ru.neverland.core.AuditTrail.item(decode(t.itemData())),t.amount(),ru.neverland.core.AuditTrail.cents(t.cents()),"contract="+t.id()+"; period-days="+t.days()+"; "+c.note());}}
    @Override public void acknowledge(SupplyContract c)throws Exception {try(var guard=ru.neverland.core.SuiteState.guard()){call("acknowledgeTrade",new Class<?>[]{UUID.class,UUID.class},c.terms().seller(),c.attempt().id());}}
    /** Legacy boolean entry points cannot promise an asynchronous budget acknowledgement. */
    @Override public boolean debit(SupplyContract c){throw new IllegalStateException("Поставка требует подтверждённого асинхронного намерения");}
    @Override public boolean credit(SupplyContract c){throw new IllegalStateException("Поставка требует подтверждённого асинхронного намерения");}
    public java.util.function.Supplier<Boolean> preparePayment(SupplyContract c,boolean deposit){try(var guard=ru.neverland.core.SuiteState.guard()){
        Town town=towny.town(deposit?c.terms().seller():c.terms().buyer());if(town==null||!TownyEconomyHandler.isActive()||TownyEconomyHandler.activeAdapter()==null)return ()->false;Account account=town.getAccount();String token="[supply:"+c.attempt().id()+"]";long cents=c.terms().cents();double value=cents/100.0;String reason=token+" Торговый договор "+c.terms().shortId();
        return ()->{var current=com.palmergames.bukkit.towny.TownyAPI.getInstance().getTown(town.getUUID());if(current==null||!TownyEconomyHandler.isActive()||TownyEconomyHandler.activeAdapter()==null||current.getAccount()!=account)return false;if(!deposit&&Bukkit.getPluginManager().getPlugin("NeverLandTownyTreasuryPlus")!=null)throw new IllegalStateException("Установленная казна требует бюджетной квитанции");var proof=new ru.neverland.core.PaymentEvidence(account,deposit,token,cents);var observer=new AccountObserver(){public void withdrew(Account source,double amount,String note){proof.observe(source,false,amount,note);}public void deposited(Account source,double amount,String note){proof.observe(source,true,amount,note);}};
            synchronized(account){account.addObserver(observer);try{try{boolean reported=deposit?account.deposit(value,"[NLT|income|infrastructure|trade] "+reason):account.withdraw(value,reason);if(proof.observed())return true;throw new IllegalStateException("Банк вернул "+reported+" без точной квитанции; нужна сверка "+token);}catch(RuntimeException error){if(proof.observed())return true;throw error;}}finally{account.removeObserver(observer);}}
        };
    }}
}
