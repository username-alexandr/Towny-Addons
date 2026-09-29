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
    public String id() { synchronized (ru.neverland.core.SuiteState.lock()) { return name().toLowerCase(Locale.ROOT); } }
    public String title() { synchronized (ru.neverland.core.SuiteState.lock()) { return title; } }
    public String description() { synchronized (ru.neverland.core.SuiteState.lock()) { return description; } }
    public boolean bilateral() { synchronized (ru.neverland.core.SuiteState.lock()) { return bilateral; } }
    public boolean protective() { synchronized (ru.neverland.core.SuiteState.lock()) { return this==ALLIANCE || this==NONAGGRESSION || this==VASSALAGE || this==GUARANTEE; } }
    public boolean directional() { synchronized (ru.neverland.core.SuiteState.lock()) { return this==VASSALAGE || this==GUARANTEE || !bilateral; } }
    public static TreatyType parse(String value) { synchronized (ru.neverland.core.SuiteState.lock()) {
        try { return valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Тип: alliance, trade, nonaggression, vassalage, guarantee, embargo, sanctions"); }
    } }
}
