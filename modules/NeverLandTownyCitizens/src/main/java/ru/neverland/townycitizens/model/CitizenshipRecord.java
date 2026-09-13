package ru.neverland.townycitizens.model;

import java.util.Objects;
import java.util.UUID;

/** Town-scoped legal status; no passport identity or Towny membership is overwritten. */
public record CitizenshipRecord(UUID town, UUID resident, CitizenshipStatus status, long expiresAt,
                                long changedAt, String actor, String reason) {
    public CitizenshipRecord {
        Objects.requireNonNull(town); Objects.requireNonNull(resident); Objects.requireNonNull(status);
        Objects.requireNonNull(actor); Objects.requireNonNull(reason);
        if (changedAt < 0 || expiresAt < 0 || actor.isBlank() || actor.length() > 80 || reason.length() > 240)
            throw new IllegalArgumentException("Некорректная запись гражданства");
        if ((status == CitizenshipStatus.TEMPORARY) != (expiresAt > 0))
            throw new IllegalArgumentException("Срок обязателен только для временного жителя");
    }
    public String key() { return town + "/" + resident; }
    public CitizenshipStatus effective(boolean member, long now) {
        if (status == CitizenshipStatus.TEMPORARY && now >= expiresAt) return CitizenshipStatus.FOREIGNER;
        if (!member && (status == CitizenshipStatus.CITIZEN || status == CitizenshipStatus.TEMPORARY))
            return CitizenshipStatus.FOREIGNER;
        return status;
    }
}
