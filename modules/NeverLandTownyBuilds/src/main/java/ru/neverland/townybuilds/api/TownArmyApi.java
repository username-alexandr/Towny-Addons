package ru.neverland.townybuilds.api;
import java.util.Set;
import java.util.UUID;
public interface TownArmyApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("isMobilized", "soldiers", "characterAge", "legacyRoster", "completeMigration"); }
    default java.util.OptionalInt characterAge(UUID resident){return java.util.OptionalInt.empty();}
    default java.util.Map<UUID,UUID> legacyRoster(){return java.util.Map.of();}
    /** Save the consumer import before calling. Clear only an exact legacy snapshot; preserve age data. */
    default boolean completeMigration(java.util.Map<UUID,UUID> expected){return false;}
    boolean isMobilized(UUID residentId);
    Set<UUID> soldiers(UUID townId);
}
