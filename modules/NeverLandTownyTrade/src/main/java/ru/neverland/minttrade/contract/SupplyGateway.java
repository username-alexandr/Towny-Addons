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
    public boolean available(){try{access();return true;}catch(Exception ex){return false;}}
    private Object access()throws ReflectiveOperationException {
        var source=Bukkit.getPluginManager().getPlugin(plugin.getConfig().getString("warehouse.plugin","NeverLandTownyBuilds"));
        if(source==null||!source.isEnabled())throw new IllegalStateException("NeverLandTownyBuilds недоступен");
        Class<?> api=Class.forName("ru.neverland.townybuilds.api.BuildingStorageApi",true,source.getClass().getClassLoader());
        api.getMethod("reserveTrade",UUID.class,UUID.class,UUID.class,ItemStack.class,int.class);
        Object service=Bukkit.getServicesManager().load(api);if(service==null)throw new IllegalStateException("Склад не зарегистрирован");return service;
    }
    private Object call(String method,Class<?>[] signature,Object... args)throws Exception {
        Object service=access();try{return service.getClass().getMethod(method,signature).invoke(service,args);}
        catch(InvocationTargetException ex){if(ex.getCause() instanceof Exception e)throw e;throw new IllegalStateException(ex.getCause());}
    }
    public String termsReady(SupplyContract c){
        Town seller=towny.town(c.terms().seller()),buyer=towny.town(c.terms().buyer());
        if(seller==null||buyer==null)return "Один из городов удалён";
        if(!available())return "Для автопоставок нужен NeverLandTownyBuilds 0.8.7 или новее";
        if(trade.marketLevel(seller)<1||trade.marketLevel(buyer)<1)return "В обоих городах нужен действующий рынок";
        if(!TradeService.importsAllowed(buyer,seller))return "Импорт ограничен политикой покупателя";
        return taxes.supplyRestriction(seller.getUUID(),buyer.getUUID());
    }
    @Override public String ready(SupplyContract c){
        if(!plugin.getConfig().getBoolean("contracts.enabled",true))return "Автопоставки отключены администратором";
        String blocked=termsReady(c);if(blocked!=null)return blocked;
        Town buyer=towny.town(c.terms().buyer());
        if(!TownyEconomyHandler.isActive())return "Экономика Towny отключена";
        try{if(!TreasuryAccess.canSpend(buyer,"infrastructure",c.terms().cents()/100.0))return "Не хватает денег в казне или бюджете инфраструктуры";}
        catch(RuntimeException ex){return "Бюджет инфраструктуры недоступен";}
        return null;
    }
    public static ItemStack decode(String data){return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));}
    @Override public String reserve(SupplyContract c)throws Exception {return (String)call("reserveTrade",new Class<?>[]{UUID.class,UUID.class,UUID.class,ItemStack.class,int.class},c.terms().seller(),c.terms().buyer(),c.attempt().id(),decode(c.terms().itemData()),c.terms().amount());}
    @Override public String settle(SupplyContract c,boolean deliver)throws Exception {return (String)call("settleTrade",new Class<?>[]{UUID.class,UUID.class,boolean.class},c.terms().seller(),c.attempt().id(),deliver);}
    @Override public void acknowledge(SupplyContract c)throws Exception {call("acknowledgeTrade",new Class<?>[]{UUID.class,UUID.class},c.terms().seller(),c.attempt().id());}
    @Override public boolean debit(SupplyContract c)throws Exception {return payment(c,false);}
    @Override public boolean credit(SupplyContract c)throws Exception {return payment(c,true);}
    private boolean payment(SupplyContract c,boolean deposit)throws Exception {
        Town town=towny.town(deposit?c.terms().seller():c.terms().buyer());if(town==null)return false;
        Account account=town.getAccount();String token="[supply:"+c.attempt().id()+"]";
        double value=c.terms().cents()/100.0;boolean[] observed={false};
        var observer=new AccountObserver(){
            private void record(Account source,double amount,String reason,boolean incoming){
                if(source==account&&incoming==deposit&&reason!=null&&reason.contains(token)&&Math.abs(amount-value)<0.000001)observed[0]=true;
            }
            public void withdrew(Account source,double amount,String reason){record(source,amount,reason,false);}
            public void deposited(Account source,double amount,String reason){record(source,amount,reason,true);}
        };
        synchronized(account){
            account.addObserver(observer);
            try {
                String reason=token+" Торговый договор "+c.terms().shortId();
                boolean result;
                try {result=deposit?TreasuryAccess.deposit(town,"infrastructure","trade",false,value,reason)
                    :TreasuryAccess.withdraw(town,"infrastructure","trade",value,reason);}
                catch(RuntimeException ex){if(observed[0])return true;throw ex;}
                if(observed[0])return true;
                if(result)throw new IllegalStateException("Банк сообщил об успехе без квитанции Towny: "+token);
                return false;
            }finally{account.removeObserver(observer);}
        }
    }
}
