package ru.neverland.townycitizens.model;

import java.util.Locale;

public enum CitizenshipStatus {
    CITIZEN("Гражданин"), TEMPORARY("Временный житель"), FOREIGNER("Иностранец"), HONORARY("Почётный гражданин");

    private final String title;
    CitizenshipStatus(String title) { this.title = title; }
    public String title() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return title; } }
    public static CitizenshipStatus parse(String value) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "citizen", "гражданин" -> CITIZEN;
            case "temporary", "временный" -> TEMPORARY;
            case "foreigner", "иностранец" -> FOREIGNER;
            case "honorary", "почётный", "почетный" -> HONORARY;
            default -> throw new IllegalArgumentException("Неизвестный статус: " + value);
        };
    } }
}
