package ru.neverland.townypopulation.api;

import ru.neverland.townypopulation.model.Capacity;
import ru.neverland.townypopulation.model.PopulationMath.Metrics;
import java.util.*;

public record PopulationSnapshot(UUID townId, String townName, int population, Capacity capacity,
                                 Metrics metrics, int lastChange, long nextCycle, boolean paused,
                                 Map<String,Integer> buildingLevels) {
    public PopulationSnapshot { buildingLevels = Map.copyOf(buildingLevels); }
}
