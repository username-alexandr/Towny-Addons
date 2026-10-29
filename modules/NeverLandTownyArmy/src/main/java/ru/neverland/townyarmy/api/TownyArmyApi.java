package ru.neverland.townyarmy.api;
import java.util.*;
/** Immutable confirmed snapshots and receipted stock operations. Resource units are thousandths. */
public interface TownyArmyApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities(){return Set.of("healthy","isMobilized","soldiers","garrison","serviceRecord","roster","reserves","history","consumeRaidSupplies","requestRaidSupplies","raidSupplyReceipt");}
    /** True only after the stock and immutable receipt are both durably acknowledged. */
    default java.util.concurrent.CompletionStage<Boolean> requestRaidSupplies(UUID town,UUID receipt,Map<String,Long> amounts)throws Exception{throw new UnsupportedOperationException();}
    default boolean raidSupplyReceipt(UUID town,UUID receipt,Map<String,Long> amounts){throw new UnsupportedOperationException();}
    /** Legacy callers may replay confirmed receipts only; new withdrawals require requestRaidSupplies. */
    default boolean consumeRaidSupplies(UUID town,UUID receipt,Map<String,Long> amounts)throws Exception{throw new UnsupportedOperationException();}
    boolean healthy();boolean isMobilized(UUID resident);Set<UUID> soldiers(UUID town);
    Optional<Map<String,Object>> garrison(UUID town);Optional<Map<String,Object>> serviceRecord(UUID resident);
    Collection<Map<String,Object>> roster(UUID town);Map<String,Long> reserves(UUID town);Collection<Map<String,Object>> history(UUID town);
}
