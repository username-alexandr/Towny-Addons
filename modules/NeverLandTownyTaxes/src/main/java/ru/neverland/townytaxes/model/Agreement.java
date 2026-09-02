package ru.neverland.townytaxes.model;

import java.util.UUID;

public record Agreement(UUID id, Domain.Scope firstScope, UUID firstId, String firstName,
                        Domain.Scope secondScope, UUID secondId, String secondName,
                        Domain.AgreementType type, double value, long createdAt, long expiresAt,
                        Domain.AgreementStatus status, String createdBy) {
    public String shortId() { return id.toString().substring(0, 8); }
    public boolean active(long now) { return status == Domain.AgreementStatus.ACTIVE && (expiresAt <= 0 || now < expiresAt); }
    public Agreement status(Domain.AgreementStatus next) { return new Agreement(id, firstScope, firstId, firstName,
            secondScope, secondId, secondName, type, value, createdAt, expiresAt, next, createdBy); }
    public boolean party(Domain.Scope scope, UUID id) { return (firstScope == scope && firstId.equals(id)) || (secondScope == scope && secondId.equals(id)); }
    public boolean pair(Domain.Scope aScope, UUID a, Domain.Scope bScope, UUID b) {
        return (firstScope == aScope && firstId.equals(a) && secondScope == bScope && secondId.equals(b))
                || (firstScope == bScope && firstId.equals(b) && secondScope == aScope && secondId.equals(a));
    }
}
