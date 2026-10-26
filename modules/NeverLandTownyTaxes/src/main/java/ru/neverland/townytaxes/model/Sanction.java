package ru.neverland.townytaxes.model;

import java.util.UUID;

public record Sanction(UUID id, Domain.Scope targetScope, UUID targetId, String targetName,
                       Domain.Scope issuerScope, UUID issuerId, String issuerName,
                       Domain.SanctionEffect effect, double value, long createdAt, long expiresAt,
                       String reason, String createdBy) {
    public String shortId() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return id.toString().substring(0, 8); } }
    public boolean active(long now) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return expiresAt <= 0 || now < expiresAt; } }
}
