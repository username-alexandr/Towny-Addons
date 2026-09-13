package ru.neverland.townycouncil.api;

import java.util.*;
import ru.neverland.core.ApiContract;

/** All calls on the server main thread. No Bukkit or internal model types cross plugin boundaries. */
public interface TownyCouncilApi extends ApiContract {
    @Override default int apiVersion() { return 1; }
    @Override default Set<String> capabilities() { return Set.of("healthy", "holders", "role", "permissions", "allows"); }
    boolean healthy();
    /** Active holders only. Keys: economy, defense, construction, foreign. */
    Map<String, UUID> holders(UUID town);
    String role(UUID town, UUID resident);
    Set<String> permissions(String role);
    /** Current assignment and eligibility; consumers must ALSO check Player.hasPermission. */
    boolean allows(UUID town, UUID resident, String permission);
}
