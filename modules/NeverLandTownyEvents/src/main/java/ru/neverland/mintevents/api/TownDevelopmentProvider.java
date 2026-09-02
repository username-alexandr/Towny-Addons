package ru.neverland.mintevents.api;

import java.util.UUID;

/** Optional bridge for another MintTowny development plugin. */
public interface TownDevelopmentProvider {
    int buildingLevel(UUID townId, String buildingId);
    int ideologyLevel(UUID townId, String ideologyId);
}
