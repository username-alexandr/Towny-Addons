package ru.neverland.townybuilds.service;

import java.util.List;

public record ContributionResult(Status status, int totalItems, List<String> details) {
    public ContributionResult {
        details = List.copyOf(details);
    }

    public static ContributionResult of(Status status) {
        return new ContributionResult(status, 0, List.of());
    }

    public enum Status {
        SUCCESS,
        NO_TOWN,
        NOT_MAYOR,
        MAX_LEVEL,
        NOTHING_NEEDED,
        NOTHING_MATCHED,
        INVENTORY_SYNC_FAILED
    }
}
