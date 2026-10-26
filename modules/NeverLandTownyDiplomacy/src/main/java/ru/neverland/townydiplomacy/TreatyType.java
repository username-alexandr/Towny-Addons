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
    public String id() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return name().toLowerCase(Locale.ROOT); } }
    public String title() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return title; } }
    public String description() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return description; } }
    public boolean bilateral() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return bilateral; } }
    public boolean protective() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return this==ALLIANCE || this==NONAGGRESSION || this==VASSALAGE || this==GUARANTEE; } }
    public boolean directional() { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { return this==VASSALAGE || this==GUARANTEE || !bilateral; } }
    public static TreatyType parse(String value) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        try { return valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Тип: alliance, trade, nonaggression, vassalage, guarantee, embargo, sanctions"); }
    } }
}
