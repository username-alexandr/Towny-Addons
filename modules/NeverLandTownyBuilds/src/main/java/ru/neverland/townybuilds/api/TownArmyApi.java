package ru.neverland.townybuilds.api;
import java.util.Set;
import java.util.UUID;
public interface TownArmyApi {
    boolean isMobilized(UUID residentId);
    Set<UUID> soldiers(UUID townId);
}
