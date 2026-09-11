package ru.neverland.townybuilds.api;
import java.util.Set;
import java.util.UUID;
public interface TownArmyApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("isMobilized", "soldiers"); }
    boolean isMobilized(UUID residentId);
    Set<UUID> soldiers(UUID townId);
}
