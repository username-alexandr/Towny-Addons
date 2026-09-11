package ru.neverland.townybuilds.api;

import ru.neverland.townybuilds.civic.CivicArea;
import ru.neverland.townybuilds.civic.CivicLine;

import java.util.Optional;
import java.util.UUID;

/** Публичный API без жёстких зависимостей между аддонами NeverLand. */
public interface TownyBuildsApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("supportedProjects", "area", "benefit", "buildingFootprints", "consumeInsurance", "insuranceReserve", "line", "operationalLevel", "projectLevel", "waterNetworkActive", "workplaces"); }
    /** Physical completed level, including inactive buildings. */
    default java.util.Set<String> supportedProjects(){return java.util.Set.of();}
    int projectLevel(UUID townId, String projectId);
    /** Zero while upkeep or power suspends this building. */
    default int operationalLevel(UUID townId,String projectId){return projectLevel(townId,projectId);}
    default java.util.Map<String,BuildingFootprint> buildingFootprints(UUID townId) { return java.util.Map.of(); }
    default java.util.Map<String,BuildingWorkplace> workplaces(UUID townId){return java.util.Map.of();}
    double benefit(UUID townId, CivicBenefit benefit);
    boolean waterNetworkActive(UUID townId);
    double insuranceReserve(UUID townId);
    double consumeInsurance(UUID townId, double requestedAmount);
    Optional<CivicArea> area(UUID townId, String projectId);
    Optional<CivicLine> line(UUID townId, String projectId);
}
