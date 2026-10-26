package ru.neverland.mintespionage.util;

public final class EspionageMath {
    private EspionageMath() { }
    public static double chance(double base, int networkLevel, double networkBonus, int defenseLevel, double defensePenalty,
                                double buildingPenalty, double minimum, double maximum) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        return clamp(base + networkLevel * networkBonus - defenseLevel * defensePenalty - buildingPenalty, minimum, maximum);
    } }
    public static double detection(double base, int defenseLevel, double defenseBonus, double buildingBonus,
                                   double minimum, double maximum) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        return clamp(base + defenseLevel * defenseBonus + buildingBonus, minimum, maximum);
    } }
    public static long duration(long baseMillis, int networkLevel, double reductionPerLevel) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        return Math.max(1000L, Math.round(baseMillis * Math.max(0.25, 1.0 - networkLevel * reductionPerLevel)));
    } }
    public static double clamp(double value, double minimum, double maximum) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return Math.max(minimum, Math.min(maximum, value)); } }
}
