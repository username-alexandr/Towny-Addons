package ru.neverland.townybuilds.api;
import java.util.*;
import java.util.concurrent.CompletableFuture;
/** Read-only development estimates; no reservation, bank operation or automatic building. */
public interface BuildingPlanningApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities(){return Set.of("development");}
    /** Empty goals return the current catalog. Nonempty goals include unmet dependencies. */
    CompletableFuture<Map<String,Object>> development(UUID town,Map<String,Integer> goals);
}
