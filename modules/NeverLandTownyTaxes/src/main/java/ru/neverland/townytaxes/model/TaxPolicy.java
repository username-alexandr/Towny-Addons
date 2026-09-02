package ru.neverland.townytaxes.model;

import java.util.UUID;

public record TaxPolicy(UUID id, Domain.Scope scope, UUID targetId, String targetName,
                        Domain.TaxType type, double value, Domain.Scope destinationScope,
                        UUID destinationId, String destinationName, long intervalMillis,
                        long nextCollection, boolean enabled, String createdBy) {
    public String shortId() { return id.toString().substring(0, 8); }
    public TaxPolicy next(long timestamp) { return new TaxPolicy(id, scope, targetId, targetName, type, value,
            destinationScope, destinationId, destinationName, intervalMillis, timestamp, enabled, createdBy); }
}
