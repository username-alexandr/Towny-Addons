package ru.neverland.townyideologies.model;

import java.util.UUID;

public record TownIdeology(UUID townId, String townName, String ideologyId, int level, long selectedAt) {
    public TownIdeology withLevel(int newLevel) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        return new TownIdeology(townId, townName, ideologyId, newLevel, selectedAt);
    } }
}
