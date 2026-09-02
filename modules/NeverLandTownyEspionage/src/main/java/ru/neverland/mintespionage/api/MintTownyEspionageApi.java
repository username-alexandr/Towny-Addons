package ru.neverland.mintespionage.api;

import java.util.UUID;

public interface MintTownyEspionageApi {
    EspionageSnapshot snapshot(UUID townId);
    double defenseStrength(UUID townId);
    int unreadReports(UUID townId);
}
