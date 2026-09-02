package ru.neverland.mintevents.api;

import java.util.Optional;
import java.util.UUID;

public interface MintTownyEventsApi {
    Optional<EventSnapshot> activeEvent(UUID townId);
    boolean addProgress(UUID townId, int points, String source);
    double protection(UUID townId);
}
