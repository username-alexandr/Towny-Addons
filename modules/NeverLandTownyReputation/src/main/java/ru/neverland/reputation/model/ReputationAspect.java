package ru.neverland.reputation.model;

import java.util.Locale;

public enum ReputationAspect {
    DIPLOMATIC("Дипломатическая"), TRADE("Торговая"), MILITARY("Военная");
    private final String title;
    ReputationAspect(String title) { this.title = title; }
    public String title() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return title; } }
    public static ReputationAspect parse(String value) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return valueOf(value.toUpperCase(Locale.ROOT)); } }
}
