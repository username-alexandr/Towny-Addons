package ru.neverland.reputation.model;

import java.util.Locale;

public enum ReputationScope {
    PLAYER, TOWN, NATION;
    public static ReputationScope parse(String value) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        if (value == null) return null;
        try { return valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return null; }
    } }
}
