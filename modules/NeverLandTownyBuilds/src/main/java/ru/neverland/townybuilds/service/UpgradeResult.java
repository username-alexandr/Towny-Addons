package ru.neverland.townybuilds.service;

import java.util.List;

public record UpgradeResult(Status status, double requiredMoney, List<String> missingResources, int newLevel) {
    public enum Status {
        SUCCESS,
        CONSTRUCTION_STARTED,
        CONSTRUCTION_IN_PROGRESS,
        CONSTRUCTION_NO_TARGET,
        CONSTRUCTION_OUTSIDE_TOWN,
        CONSTRUCTION_UNEVEN_GROUND,
        CONSTRUCTION_OBSTRUCTED,
        CONSTRUCTION_UNAVAILABLE,
        NO_TOWN,
        NOT_MAYOR,
        MAX_LEVEL,
        NOT_ENOUGH_MONEY,
        NOT_ENOUGH_RESOURCES,
        NOT_ENOUGH_ARTIFACTS,
        PREREQUISITES_NOT_MET,
        ECONOMY_ERROR
    }

    public static UpgradeResult of(Status status) {
        return new UpgradeResult(status, 0, List.of(), 0);
    }
}
