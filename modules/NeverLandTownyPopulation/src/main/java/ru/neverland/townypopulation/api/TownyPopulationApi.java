package ru.neverland.townypopulation.api;

import java.util.*;
/** Immutable cached snapshots; safe to read from async integrations. */
public interface TownyPopulationApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("population", "populations", "residentPopulation", "healthy"); }
    /** Last confirmed cached data remains readable during a write fault; inspect snapshot.paused(). */
    default boolean healthy(){return true;}
    Optional<PopulationSnapshot> population(UUID townId);
    Optional<PopulationSnapshot> residentPopulation(UUID residentId);
    Collection<PopulationSnapshot> populations();
}
