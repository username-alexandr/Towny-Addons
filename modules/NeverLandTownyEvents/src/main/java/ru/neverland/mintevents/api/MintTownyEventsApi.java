package ru.neverland.mintevents.api;

import java.util.Optional;
import java.util.UUID;

public interface MintTownyEventsApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("activeEvent", "addProgress", "protection"); }
    Optional<EventSnapshot> activeEvent(UUID townId);
    boolean addProgress(UUID townId, int points, String source);
    double protection(UUID townId);
}
