package ru.neverland.townyideologies.service;

import ru.neverland.townyideologies.model.IdeologyDefinition;

public record PurchaseResult(PurchaseStatus status, IdeologyDefinition definition, int level, double price) {
    public boolean success() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        return status == PurchaseStatus.SELECTED || status == PurchaseStatus.CHANGED || status == PurchaseStatus.UPGRADED;
    } }
}
