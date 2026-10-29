package ru.neverland.townytreasury.api;
import java.util.*;
import ru.neverland.townytreasury.model.CityLedger;
/** Money and budget writes require the server thread. No extra economy account is created. */
public interface TownyTreasuryApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("canSpend", "spend", "treasury", "forecast", "requestDebit", "debitStatus", "requestRefund", "refundStatus"); }
    /** Durable PREPARED/terminal result; poll debitStatus until PAID, REJECTED or REVIEW. */
    default java.util.concurrent.CompletionStage<Map<String,Object>> requestDebit(UUID operation,UUID town,String category,String source,long cents,String reason,UUID actor,String ownerPlugin){throw new UnsupportedOperationException();}
    default Map<String,Object> debitStatus(UUID operation){throw new UnsupportedOperationException();}
    default java.util.concurrent.CompletionStage<Map<String,Object>> requestRefund(UUID refundOperation,UUID paidDebitOperation,UUID actor,String ownerPlugin,String reason){throw new UnsupportedOperationException();}
    default Map<String,Object> refundStatus(UUID operation){return debitStatus(operation);}
    boolean canSpend(UUID town,String category,double amount);
    boolean spend(UUID town,String category,String source,double amount,String reason);
    Optional<CityLedger> treasury(UUID town);
    default Map<String,Object> forecast(UUID town,int seconds){throw new UnsupportedOperationException();}
}
