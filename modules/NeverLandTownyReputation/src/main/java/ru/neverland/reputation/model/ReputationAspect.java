package ru.neverland.reputation.model;

import java.util.Locale;

public enum ReputationAspect {
    DIPLOMATIC("Дипломатическая"), TRADE("Торговая"), MILITARY("Военная");
    private final String title;
    ReputationAspect(String title) { this.title = title; }
    public String title() { return title; }
    public static ReputationAspect parse(String value) { return valueOf(value.toUpperCase(Locale.ROOT)); }
}
