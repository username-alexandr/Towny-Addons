package ru.neverland.townytaxes.model;

import java.util.UUID;

public record TaxPolicy(UUID id, Domain.Scope scope, UUID targetId, String targetName,
                        Domain.TaxType type, double value, Domain.Scope destinationScope,
                        UUID destinationId, String destinationName, long intervalMillis,
                        long nextCollection, boolean enabled, String createdBy) {
    public String shortId() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return id.toString().substring(0, 8); } }
    public TaxPolicy next(long timestamp) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return new TaxPolicy(id, scope, targetId, targetName, type, value,
            destinationScope, destinationId, destinationName, intervalMillis, timestamp, enabled, createdBy); } }
}
