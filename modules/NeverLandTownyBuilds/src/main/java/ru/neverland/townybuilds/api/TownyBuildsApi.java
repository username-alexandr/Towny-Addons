package ru.neverland.townybuilds.api;

import ru.neverland.townybuilds.civic.CivicArea;
import ru.neverland.townybuilds.civic.CivicLine;

import java.util.Optional;
import java.util.UUID;

/** Публичный API без жёстких зависимостей между аддонами NeverLand. */
public interface TownyBuildsApi {
    /** Physical completed level, including inactive buildings. */
    int projectLevel(UUID townId, String projectId);
    /** Zero while upkeep suspends this building. */
    default int operationalLevel(UUID townId,String projectId){return projectLevel(townId,projectId);}
    default java.util.Map<String,BuildingFootprint> buildingFootprints(UUID townId) { return java.util.Map.of(); }
    double benefit(UUID townId, CivicBenefit benefit);
    boolean waterNetworkActive(UUID townId);
    double insuranceReserve(UUID townId);
    double consumeInsurance(UUID townId, double requestedAmount);
    Optional<CivicArea> area(UUID townId, String projectId);
    Optional<CivicLine> line(UUID townId, String projectId);
}
