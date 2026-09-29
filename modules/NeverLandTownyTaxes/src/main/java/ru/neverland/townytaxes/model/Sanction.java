package ru.neverland.townytaxes.model;

import java.util.UUID;

public record Sanction(UUID id, Domain.Scope targetScope, UUID targetId, String targetName,
                       Domain.Scope issuerScope, UUID issuerId, String issuerName,
                       Domain.SanctionEffect effect, double value, long createdAt, long expiresAt,
                       String reason, String createdBy) {
    public String shortId() { synchronized (ru.neverland.core.SuiteState.lock()) { return id.toString().substring(0, 8); } }
    public boolean active(long now) { synchronized (ru.neverland.core.SuiteState.lock()) { return expiresAt <= 0 || now < expiresAt; } }
}
