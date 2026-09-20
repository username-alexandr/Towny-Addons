package ru.neverland.mintevents.api;

import java.util.Optional;
import java.util.UUID;

public interface MintTownyEventsApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("raidVictories", "activeEvent", "addProgress", "protection", "requiresRepair", "hasFireDamage", "productionMultiplier", "paused", "shieldRemainingMillis"); }
    default boolean paused(UUID townId) { return false; }
    default long shieldRemainingMillis(UUID townId) { return 0; }
    /** Durable count of completed raid victories. Forced resolutions do not increment it. */
    default long raidVictories(UUID townId) { throw new UnsupportedOperationException("raidVictories"); }
    Optional<EventSnapshot> activeEvent(UUID townId);
    boolean addProgress(UUID townId, int points, String source);
    double protection(UUID townId);
    /** Future agrarian output during a drought/flood; does not modify stored resources. */
    default double productionMultiplier(UUID townId, String building) { return 1; }
    /** True also when the damage ledger is unavailable: automatic repair must stop. */
    default boolean requiresRepair(UUID world, int x, int y, int z) { return true; }
    default boolean hasFireDamage(UUID townId) { return true; }
}
