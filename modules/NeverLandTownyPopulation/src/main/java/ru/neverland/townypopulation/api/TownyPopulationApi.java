package ru.neverland.townypopulation.api;

import java.util.*;
/** Immutable cached snapshots; safe to read from async integrations. */
public interface TownyPopulationApi {
    Optional<PopulationSnapshot> population(UUID townId);
    Optional<PopulationSnapshot> residentPopulation(UUID residentId);
    Collection<PopulationSnapshot> populations();
}
