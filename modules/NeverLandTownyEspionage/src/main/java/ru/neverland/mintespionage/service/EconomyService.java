package ru.neverland.mintespionage.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintespionage.model.OperationDefinition;
import ru.neverland.mintespionage.util.ColorUtil;

import java.text.DecimalFormat;

public final class EconomyService {
    private final JavaPlugin plugin;private final DecimalFormat format=new DecimalFormat("#,##0.##");
    public EconomyService(JavaPlugin plugin){this.plugin=plugin;}
    public double balance(Town town){try(var guard=ru.neverland.core.SuiteState.guard()){if(town==null||!com.palmergames.bukkit.towny.TownyEconomyHandler.isActive())throw new IllegalStateException("Экономика города недоступна");double value=town.getAccount().getHoldingBalance(true);if(!Double.isFinite(value)||value<0)throw new IllegalStateException("Баланс недоступен");return value;}}
    public String balanceText(Town town){try{return format(balance(town));}catch(RuntimeException unavailable){return "временно недоступна";}}
    public static final String PROVIDER="NeverLandTownyTreasuryPlus",CONTRACT="ru.neverland.townytreasury.api.TownyTreasuryApi";
    public boolean purchaseAvailable(){try{ru.neverland.core.ApiServices.require(PROVIDER,CONTRACT,"requestDebit","debitStatus","requestRefund","refundStatus");return true;}catch(RuntimeException unavailable){return false;}}
    public java.util.Map<?,?> status(SpyPurchases.Terms t)throws ReflectiveOperationException{var api=ru.neverland.core.ApiServices.require(PROVIDER,CONTRACT,"debitStatus");Object raw=api.invoke("debitStatus",new Class<?>[]{java.util.UUID.class},t.id());if(raw instanceof java.util.Map<?,?> row&&"ABSENT".equals(row.get("status"))&&t.id().toString().equals(row.get("operation")))return row;return verify(raw,t,t.id(),false);}
    public java.util.concurrent.CompletionStage<java.util.Map<?,?>> request(SpyPurchases.Terms t)throws ReflectiveOperationException{try(var guard=ru.neverland.core.SuiteState.guard()){
        var api=ru.neverland.core.ApiServices.require(PROVIDER,CONTRACT,"requestDebit","debitStatus");Object raw=api.invoke("requestDebit",new Class<?>[]{java.util.UUID.class,java.util.UUID.class,String.class,String.class,long.class,String.class,java.util.UUID.class,String.class},t.id(),t.town(),"army","espionage",t.cents(),t.reason(),t.actor(),plugin.getName());
        if(!(raw instanceof java.util.concurrent.CompletionStage<?> response))throw new IllegalStateException("Казна не вернула подтверждаемую квитанцию");return response.thenApply(value->verify(value,t,t.id(),false));
    }}
    public java.util.concurrent.CompletionStage<java.util.Map<?,?>> refund(SpyPurchases.Purchase p)throws ReflectiveOperationException{try(var guard=ru.neverland.core.SuiteState.guard()){var t=p.terms();var api=ru.neverland.core.ApiServices.require(PROVIDER,CONTRACT,"requestRefund","refundStatus");Object raw=api.invoke("requestRefund",new Class<?>[]{java.util.UUID.class,java.util.UUID.class,java.util.UUID.class,String.class,String.class},p.refundId(),t.id(),t.actor(),plugin.getName(),refundReason(t));if(!(raw instanceof java.util.concurrent.CompletionStage<?> stage))throw new IllegalStateException("Казна не вернула квитанцию возврата");return stage.thenApply(value->verify(value,t,p.refundId(),true));}}
    private String refundReason(SpyPurchases.Terms t){return "Возврат покупки разведки "+t.id();}
    private java.util.Map<?,?> verify(Object value,SpyPurchases.Terms t,java.util.UUID operation,boolean refund){if(!(value instanceof java.util.Map<?,?> row)||!operation.toString().equals(row.get("operation"))||!t.town().toString().equals(row.get("town"))||!"army".equals(row.get("category"))||!"espionage".equals(row.get("source"))||!(row.get("cents") instanceof Long cents)||cents!=t.cents()||!(refund?refundReason(t):t.reason()).equals(row.get("reason"))||!t.actor().toString().equals(row.get("actor"))||!plugin.getName().equals(row.get("owner"))||!(refund?"REFUND":"DEBIT").equals(row.get("kind"))||!(refund?t.id().toString():"").equals(row.get("backingDebit"))||!(row.get("status") instanceof String))throw new IllegalStateException("Квитанция казны относится к другой покупке");return row;}
    public String operationReason(OperationDefinition definition,String target){return ColorUtil.strip(plugin.getConfig().getString("economy.operation-reason","Разведывательная операция").replace("%operation%",ColorUtil.strip(definition.name())).replace("%target%",target));}
    public String upgradeReason(boolean network){return ColorUtil.strip(plugin.getConfig().getString(network?"economy.network-upgrade-reason":"economy.defense-upgrade-reason","Развитие разведки"));}
    public String format(double value){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {return format.format(value);} }
}
