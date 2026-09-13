package ru.neverland.townydiplomacy;

import java.util.*;

public enum TreatyType {
    ALLIANCE("Союз", "Взаимная защита и запрет нападений", true),
    TRADE("Торговый договор", "Льгота на пошлины сторон договора", true),
    NONAGGRESSION("Пакт о ненападении", "Запрет PvP и разведопераций", true),
    VASSALAGE("Вассалитет", "Первый город — сюзерен; второй — вассал", true),
    GUARANTEE("Гарантия независимости", "Первый город защищает независимость второго", true),
    EMBARGO("Эмбарго", "Запрет торговли между двумя городами", false),
    SANCTIONS("Санкции", "Торговые и/или дипломатические ограничения", false);
    private final String title, description; private final boolean bilateral;
    TreatyType(String title, String description, boolean bilateral) { this.title=title; this.description=description; this.bilateral=bilateral; }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String title() { return title; }
    public String description() { return description; }
    public boolean bilateral() { return bilateral; }
    public boolean protective() { return this==ALLIANCE || this==NONAGGRESSION || this==VASSALAGE || this==GUARANTEE; }
    public boolean directional() { return this==VASSALAGE || this==GUARANTEE || !bilateral; }
    public static TreatyType parse(String value) {
        try { return valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Тип: alliance, trade, nonaggression, vassalage, guarantee, embargo, sanctions"); }
    }
}
