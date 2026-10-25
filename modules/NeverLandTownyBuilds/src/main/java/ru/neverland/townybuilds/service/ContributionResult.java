package ru.neverland.townybuilds.service;

import java.util.List;

public record ContributionResult(Status status, int totalItems, List<String> details) {
    public ContributionResult {
        details = List.copyOf(details);
    }

    public static ContributionResult of(Status status) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return new ContributionResult(status, 0, List.of());
    } }

    public enum Status {
        SPECIALIZATION_LOCKED,
        SUCCESS,
        NO_TOWN,
        NOT_MAYOR,
        MAX_LEVEL,
        NOTHING_NEEDED,
        NOTHING_MATCHED,
        STORAGE_BUSY,
        CONSTRUCTION_ACTIVE,
        INVENTORY_SYNC_FAILED
    }
}
