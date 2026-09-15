package ru.neverland.townyarmy.api;
import java.util.*;
/** Read-only main-thread API. Immutable JDK snapshots; reserves use thousandths. */
public interface TownyArmyApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities(){return Set.of("healthy","isMobilized","soldiers","garrison","serviceRecord","roster","reserves","history");}
    boolean healthy();boolean isMobilized(UUID resident);Set<UUID> soldiers(UUID town);
    Optional<Map<String,Object>> garrison(UUID town);Optional<Map<String,Object>> serviceRecord(UUID resident);
    Collection<Map<String,Object>> roster(UUID town);Map<String,Long> reserves(UUID town);Collection<Map<String,Object>> history(UUID town);
}
