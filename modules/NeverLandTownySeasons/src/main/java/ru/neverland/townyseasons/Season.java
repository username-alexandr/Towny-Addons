package ru.neverland.townyseasons;

import java.util.Locale;

public enum Season {
    SPRING("Весна"), SUMMER("Лето"), AUTUMN("Осень"), WINTER("Зима");
    public final String title;
    Season(String title) { this.title = title; }
    public static Season parse(String value) {
        String name = value.toUpperCase(Locale.ROOT);
        return valueOf(name.equals("FALL") ? "AUTUMN" : name);
    }
}
