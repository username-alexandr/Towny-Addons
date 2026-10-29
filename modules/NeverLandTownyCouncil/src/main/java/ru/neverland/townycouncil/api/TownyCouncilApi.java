package ru.neverland.townycouncil.api;

import java.util.*;
import ru.neverland.core.ApiContract;

/** All calls on the server main thread. No Bukkit or internal model types cross plugin boundaries. */
public interface TownyCouncilApi extends ApiContract {
    @Override default int apiVersion() { return 1; }
    @Override default Set<String> capabilities() { return Set.of("healthy", "holders", "role", "permissions", "allows", "decision", "actions", "roleActions", "residentActions", "temporaryRights", "spendingPolicy"); }
    /** Restrictions only. Amounts are integer cents; zero means unlimited, unavailable never means unlimited. */
    default Map<String,Object> spendingPolicy(UUID town,UUID resident){return Map.of("available",false);}
    /** ALLOW/DENY overrides the caller's city-local default; DEFAULT preserves it. */
    default String decision(UUID town,UUID resident,String action){return "DEFAULT";}
    default Map<String,String> actions(){return Map.of();}
    default Map<String,String> roleActions(UUID town,String role){return Map.of();}
    default Map<String,String> residentActions(UUID town,UUID resident){return Map.of();}
    /** Active temporary actions and their exact expiry; permanent rules remain separate. */
    default Map<String,Long> temporaryRights(UUID town,UUID resident){return Map.of();}
    boolean healthy();
    /** Active holders only. Keys: economy, defense, construction, foreign. */
    Map<String, UUID> holders(UUID town);
    String role(UUID town, UUID resident);
    Set<String> permissions(String role);
    /** Current assignment and eligibility; consumers must ALSO check Player.hasPermission. */
    boolean allows(UUID town, UUID resident, String permission);
}
