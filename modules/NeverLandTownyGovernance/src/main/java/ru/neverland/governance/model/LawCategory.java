package ru.neverland.governance.model;

import java.util.Locale;

public enum LawCategory {
    TAX("Налоги"), CONSTRUCTION("Строительство"), IDEOLOGY("Идеология"), CIVIC("Гражданское управление");

    private final String display;
    LawCategory(String display) { this.display = display; }
    public String display() { synchronized (ru.neverland.core.SuiteState.lock()) { return display; } }

    public static LawCategory parse(String value) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if (value == null) return CIVIC;
        try { return valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return CIVIC; }
    } }
}
