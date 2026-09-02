package ru.neverland.townybuilds.api;

import ru.neverland.townybuilds.civic.CivicArea;
import ru.neverland.townybuilds.civic.CivicLine;

import java.util.Optional;
import java.util.UUID;

/** Публичный API без жёстких зависимостей между аддонами NeverLand. */
public interface TownyBuildsApi {
    int projectLevel(UUID townId, String projectId);
    double benefit(UUID townId, CivicBenefit benefit);
    boolean waterNetworkActive(UUID townId);
    double insuranceReserve(UUID townId);
    double consumeInsurance(UUID townId, double requestedAmount);
    Optional<CivicArea> area(UUID townId, String projectId);
    Optional<CivicLine> line(UUID townId, String projectId);
}
