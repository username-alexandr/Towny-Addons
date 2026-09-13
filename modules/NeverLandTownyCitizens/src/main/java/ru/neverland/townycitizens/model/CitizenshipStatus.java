package ru.neverland.townycitizens.model;

import java.util.Locale;

public enum CitizenshipStatus {
    CITIZEN("Гражданин"), TEMPORARY("Временный житель"), FOREIGNER("Иностранец"), HONORARY("Почётный гражданин");

    private final String title;
    CitizenshipStatus(String title) { this.title = title; }
    public String title() { return title; }
    public static CitizenshipStatus parse(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "citizen", "гражданин" -> CITIZEN;
            case "temporary", "временный" -> TEMPORARY;
            case "foreigner", "иностранец" -> FOREIGNER;
            case "honorary", "почётный", "почетный" -> HONORARY;
            default -> throw new IllegalArgumentException("Неизвестный статус: " + value);
        };
    }
}
