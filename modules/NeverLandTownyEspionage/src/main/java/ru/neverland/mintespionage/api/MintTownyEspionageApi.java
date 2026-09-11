package ru.neverland.mintespionage.api;

import java.util.UUID;

public interface MintTownyEspionageApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("defenseStrength", "snapshot", "unreadReports"); }
    EspionageSnapshot snapshot(UUID townId);
    double defenseStrength(UUID townId);
    int unreadReports(UUID townId);
}
