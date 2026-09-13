package ru.neverland.townycouncil;

import java.util.Locale;

public enum MinisterRole {
    ECONOMY("Экономика", "Бюджет, городские налоги и отчёты"),
    DEFENSE("Оборона", "Армия и разведывательные операции"),
    CONSTRUCTION("Строительство", "Городские проекты и ресурсы склада"),
    FOREIGN("Внешняя политика", "Соглашения, санкции и торговля");
    private final String title, description;
    MinisterRole(String title, String description) { this.title = title; this.description = description; }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String title() { return title; }
    public String description() { return description; }
    public String permission() { return "neverlandtownycouncil.minister." + id(); }
    public static MinisterRole parse(String text) {
        try { return valueOf(text.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Должность: economy, defense, construction или foreign"); }
    }
}
