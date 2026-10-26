package ru.neverland.governance.model;

import java.util.Locale;

public enum LawCategory {
    TAX("Налоги"), CONSTRUCTION("Строительство"), IDEOLOGY("Идеология"), CIVIC("Гражданское управление");

    private final String display;
    LawCategory(String display) { this.display = display; }
    public String display() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return display; } }

    public static LawCategory parse(String value) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if (value == null) return CIVIC;
        try { return valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return CIVIC; }
    } }
}
