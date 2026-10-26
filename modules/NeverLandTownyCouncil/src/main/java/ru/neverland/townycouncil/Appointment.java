package ru.neverland.townycouncil;

import java.util.Objects;
import java.util.UUID;

public record Appointment(UUID town, MinisterRole role, UUID resident, UUID mayor, long appointedAt, String actor) {
    public Appointment {
        Objects.requireNonNull(town); Objects.requireNonNull(role); Objects.requireNonNull(resident); Objects.requireNonNull(mayor);
        if (appointedAt < 0 || actor == null || actor.isBlank() || actor.length() > 200 || resident.equals(mayor))
            throw new IllegalArgumentException("Повреждено назначение министра");
    }
    public String key() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return town + "/" + role.id(); } }
}
