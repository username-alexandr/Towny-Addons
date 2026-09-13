package ru.neverland.core;

import java.util.Set;
import java.util.UUID;

/** Stable business receipt; retry identity and occurrence time must survive restarts. */
public record ReputationOutcome(String id, String scope, UUID subject, String rule, long at, String context) {
    public ReputationOutcome {
        if (id == null || id.isBlank() || id.length() > 240 || !Set.of("PLAYER", "TOWN", "NATION").contains(scope)
                || subject == null || rule == null || !rule.matches("[A-Z_]{1,64}") || at < 0
                || context == null || context.length() > 400) throw new IllegalArgumentException("Некорректное событие репутации");
    }
    public static ReputationOutcome town(String id, UUID town, String rule, long at, String context) {
        return new ReputationOutcome(id + ":" + town, "TOWN", town, rule, at, context);
    }
}
