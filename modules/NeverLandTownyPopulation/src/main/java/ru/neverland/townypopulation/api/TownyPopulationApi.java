package ru.neverland.townypopulation.api;

import java.util.*;
/** Immutable cached snapshots; safe to read from async integrations. */
public interface TownyPopulationApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("population", "populations", "residentPopulation"); }
    Optional<PopulationSnapshot> population(UUID townId);
    Optional<PopulationSnapshot> residentPopulation(UUID residentId);
    Collection<PopulationSnapshot> populations();
}
