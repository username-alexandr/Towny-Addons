package ru.neverland.townyideologies.service;

import ru.neverland.townyideologies.model.IdeologyDefinition;

public record PurchaseResult(PurchaseStatus status, IdeologyDefinition definition, int level, double price) {
    public boolean success() {
        return status == PurchaseStatus.SELECTED || status == PurchaseStatus.CHANGED || status == PurchaseStatus.UPGRADED;
    }
}
