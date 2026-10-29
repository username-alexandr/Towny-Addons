package ru.neverland.townymarket;
import java.util.*;import java.util.concurrent.*;import ru.neverland.core.*;import org.bukkit.Bukkit;
/** Frozen city budget request; the original order remains authoritative for goods and consent. */
public final class MarketFunding {
 public static final String OWNER="NeverLandTownyMarket";
 public enum Result{WAIT,PAID,REJECTED,REVIEW}
 public record Terms(UUID operation,UUID town,UUID actor,long cents,String category,String reason){
  public Terms{Objects.requireNonNull(operation);Objects.requireNonNull(town);Objects.requireNonNull(actor);if(cents<1||cents>MarketData.MAX||category==null||!Set.of("army","construction","infrastructure","social","free").contains(category)||reason==null||reason.isBlank()||reason.length()>512)throw new IllegalArgumentException("Повреждены условия оплаты рынка");}
  public Result result(Map<?,?> row){if(!operation.toString().equals(row.get("operation")))throw new IllegalStateException("Квитанция другой покупки");if("ABSENT".equals(row.get("status")))return Result.WAIT;
   if(!town.toString().equals(row.get("town"))||!actor.toString().equals(row.get("actor"))||!category.equals(row.get("category"))||!reason.equals(row.get("reason"))||!OWNER.equals(row.get("owner"))||!"trade".equals(row.get("source"))||!"DEBIT".equals(row.get("kind"))||!"".equals(row.get("backingDebit"))||!(row.get("cents") instanceof Number n)||new java.math.BigDecimal(n.toString()).compareTo(java.math.BigDecimal.valueOf(cents))!=0)throw new IllegalStateException("Квитанция отличается от подтверждённой покупки");
   return switch(String.valueOf(row.get("status"))){case "PAID"->Boolean.TRUE.equals(row.get("backingAvailable"))?Result.PAID:Result.REVIEW;case "REJECTED"->Result.REJECTED;case "PREPARED","INVOKING"->Result.WAIT;case "REVIEW"->Result.REVIEW;default->throw new IllegalStateException("Неизвестный результат банка");};
  }
 }
 public record Payment(String protocol,Terms terms){public Payment{if(!Set.of("DIRECT","TREASURY").contains(protocol)||protocol.equals("TREASURY")!=(terms!=null))throw new IllegalArgumentException("Повреждён протокол покупки");}}
 public static UUID debit(UUID order){return EffectJournal.id("market:"+order+":debit");}
 public boolean installed(){return Bukkit.getPluginManager().getPlugin("NeverLandTownyTreasuryPlus")!=null;}
 private ApiServices.Connection api(){return ApiServices.connect("NeverLandTownyTreasuryPlus","ru.neverland.townytreasury.api.TownyTreasuryApi",1,"requestDebit","debitStatus","resolveCategory");}
 public Payment freeze(MarketData.Order order)throws Exception{if(!order.city()||!installed())return new Payment("DIRECT",null);var api=api();if(!api.ready())throw new IllegalStateException("Казна временно недоступна; покупка ещё не оплачена");String category=(String)api.invoke("resolveCategory",new Class<?>[]{String.class},"infrastructure");return new Payment("TREASURY",new Terms(debit(order.id()),order.buyer(),order.actor(),order.total(),category,"Покупка на рынке "+order.id()));}
 private Map<?,?> snapshot(ApiServices.Connection api,Terms terms)throws Exception{return(Map<?,?>)api.invoke("debitStatus",new Class<?>[]{UUID.class},terms.operation());}
 public Map<?,?> snapshot(Terms terms)throws Exception{var api=api();if(!api.ready())throw new IllegalStateException("Установленная казна пока недоступна");var row=snapshot(api,terms);terms.result(row);return row;}
 @SuppressWarnings("unchecked")public CompletionStage<Result> poll(Terms terms,boolean mayRequest){try{var api=api();if(!api.ready())return CompletableFuture.completedFuture(Result.WAIT);var row=snapshot(api,terms);var known=terms.result(row);if(!"ABSENT".equals(row.get("status"))||!mayRequest)return CompletableFuture.completedFuture(known);var ack=(CompletionStage<Map<?,?>>)api.invoke("requestDebit",new Class<?>[]{UUID.class,UUID.class,String.class,String.class,long.class,String.class,UUID.class,String.class},terms.operation(),terms.town(),terms.category(),"trade",terms.cents(),terms.reason(),terms.actor(),OWNER);return ack.thenApply(terms::result);}catch(Exception error){return CompletableFuture.failedFuture(error);}}
}
