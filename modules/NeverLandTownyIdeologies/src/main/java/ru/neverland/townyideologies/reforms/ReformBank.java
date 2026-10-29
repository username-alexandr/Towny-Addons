package ru.neverland.townyideologies.reforms;

import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.economy.*;
import ru.neverland.core.*;

/** Stable Treasury UUID/payload. An installed unavailable Treasury never falls back to a direct debit. */
public class ReformBank {
    public static final String OWNER="NeverLandTownyIdeologies";
    public record Request(UUID town,UUID receipt,long cents,UUID actor){
        public Request{Objects.requireNonNull(town);Objects.requireNonNull(receipt);Objects.requireNonNull(actor);if(cents<1||cents>100_000_000_000L)throw new IllegalArgumentException("Неверная сумма реформы");}
        public String reason(){return "[ideology-reform:"+receipt+"] Завершение реформы";}
    }
    private boolean installed(){return Bukkit.getPluginManager().getPlugin("NeverLandTownyTreasuryPlus")!=null;}
    public String route(){try(var guard=SuiteState.guard()){return installed()?"TREASURY_ASYNC":"DIRECT_TOWN_BANK";}}
    private ApiServices.Connection link(String method){var link=ApiServices.connect("NeverLandTownyTreasuryPlus","ru.neverland.townytreasury.api.TownyTreasuryApi",1,method);if(!link.ready())throw new IllegalStateException("Бюджет казны недоступен; платёж сохранён без обхода бюджета");return link;}
    public boolean ready(UUID town){try(var guard=SuiteState.guard()){if(!TownyEconomyHandler.isActive()||TownyEconomyHandler.activeAdapter()==null||TownyAPI.getInstance().getTown(town)==null)return false;try{if(installed()){link("requestDebit");link("debitStatus");}return true;}catch(RuntimeException unavailable){return false;}}}
    @SuppressWarnings("unchecked")public CompletionStage<String> request(Request request,String route){
        try{
            if(route.equals("DIRECT_TOWN_BANK")){if(installed())throw new IllegalStateException("Способ оплаты изменился; требуется сверка сохранённой квитанции");return CompletableFuture.completedFuture(pay(request.town(),request.receipt(),request.cents())?"PAID":"REJECTED");}
            if(!route.equals("TREASURY_ASYNC"))throw new IllegalStateException("Исторический платёж не отправляется в банк повторно");
            final CompletionStage<?> future;try(var guard=SuiteState.guard()){Object value=link("requestDebit").invoke("requestDebit",new Class<?>[]{UUID.class,UUID.class,String.class,String.class,long.class,String.class,UUID.class,String.class},request.receipt(),request.town(),"social","ideology",request.cents(),request.reason(),request.actor(),OWNER);if(!(value instanceof CompletionStage<?> stage))throw new IllegalStateException("Несовместимый ответ бюджета казны");future=stage;}
            return future.thenApply(value->{if(!(value instanceof Map<?,?> map))throw new IllegalArgumentException("Нет точной квитанции казны");return verified(request,(Map<String,Object>)map);});
        }catch(Exception|LinkageError error){return CompletableFuture.failedFuture(error);}
    }
    @SuppressWarnings("unchecked")public String status(Request request){try(var guard=SuiteState.guard()){try{Object value=link("debitStatus").invoke("debitStatus",new Class<?>[]{UUID.class},request.receipt());if(!(value instanceof Map<?,?> map))throw new IllegalArgumentException("Нет точной квитанции казны");return verified(request,(Map<String,Object>)map);}catch(ReflectiveOperationException error){throw new IllegalStateException("Не удалось проверить сохранённый платёж казны",error);}}}
    public static String verified(Request request,Map<String,Object> row){
        if(!request.receipt().toString().equals(row.get("operation")))throw new IllegalArgumentException("Квитанция казны относится к другому платежу");
        String status=String.valueOf(row.get("status"));if(status.equals("ABSENT"))return status;
        if(!request.town().toString().equals(row.get("town"))||!"social".equals(row.get("category"))||!"ideology".equals(row.get("source"))||number(row.get("cents"))!=request.cents()||!request.reason().equals(row.get("reason"))||!request.actor().toString().equals(row.get("actor"))||!OWNER.equals(row.get("owner"))||!"DEBIT".equals(row.get("kind"))||!"".equals(row.get("backingDebit")))throw new IllegalArgumentException("Сумма, город или условия квитанции казны не совпали; нужна сверка");
        if(!Set.of("PREPARED","INVOKING","PAID","REJECTED","REVIEW").contains(status))throw new IllegalArgumentException("Неизвестный исход платежа казны");
        // Historical PAID after a pending/paid refund cannot fund a newly activated reform.
        if(status.equals("PAID")&&!Boolean.TRUE.equals(row.get("backingAvailable")))return "REVIEW";return status;
    }
    private static long number(Object value){try{if(!(value instanceof Number))throw new IllegalArgumentException("Нет суммы квитанции");return new java.math.BigDecimal(value.toString()).longValueExact();}catch(ArithmeticException error){throw new IllegalArgumentException("Неверная сумма квитанции",error);}}
    /** Only for genuinely absent Treasury; exact Towny observer evidence remains mandatory. */
    public boolean pay(UUID townId,UUID receipt,long cents){
        final com.palmergames.bukkit.towny.object.Town town;try(var guard=SuiteState.guard()){if(installed())throw new IllegalStateException("Установленная казна требует подтверждаемую заявку");if(!ready(townId))throw new IllegalStateException("Экономика Towny пока недоступна");town=TownyAPI.getInstance().getTown(townId);}double amount=cents/100.0;
        final Account account;try(var guard=SuiteState.guard()){account=town.getAccount();if(!account.canPayFromHoldings(amount))return false;}String token="[ideology-reform:"+receipt+"]";var evidence=new PaymentEvidence(account,false,token,cents);
        var observer=new AccountObserver(){public void withdrew(Account a,double n,String reason){evidence.observe(a,false,n,reason);}public void deposited(Account a,double n,String reason){}};
        try(var guard=SuiteState.guard()){synchronized(account){account.addObserver(observer);}}
        try{try{account.withdraw(amount,token+" Завершение реформы");}catch(RuntimeException error){if(evidence.observed())return true;throw error;}
            if(!evidence.observed())throw new IllegalStateException("Банк не подтвердил квитанцию списания; нужна сверка");return true;
        }finally{try(var guard=SuiteState.guard()){synchronized(account){account.removeObserver(observer);}}}
    }
}
