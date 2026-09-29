package ru.neverland.townyideologies.model;

import java.util.UUID;

public record TownIdeology(UUID townId, String townName, String ideologyId, int level, long selectedAt) {
    public TownIdeology withLevel(int newLevel) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return new TownIdeology(townId, townName, ideologyId, newLevel, selectedAt);
    } }
}
