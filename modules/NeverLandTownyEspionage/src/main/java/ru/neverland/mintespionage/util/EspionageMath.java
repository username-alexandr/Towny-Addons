package ru.neverland.mintespionage.util;

public final class EspionageMath {
    private EspionageMath() { }
    public static double chance(double base, int networkLevel, double networkBonus, int defenseLevel, double defensePenalty,
                                double buildingPenalty, double minimum, double maximum) {
        return clamp(base + networkLevel * networkBonus - defenseLevel * defensePenalty - buildingPenalty, minimum, maximum);
    }
    public static double detection(double base, int defenseLevel, double defenseBonus, double buildingBonus,
                                   double minimum, double maximum) {
        return clamp(base + defenseLevel * defenseBonus + buildingBonus, minimum, maximum);
    }
    public static long duration(long baseMillis, int networkLevel, double reductionPerLevel) {
        return Math.max(1000L, Math.round(baseMillis * Math.max(0.25, 1.0 - networkLevel * reductionPerLevel)));
    }
    public static double clamp(double value, double minimum, double maximum) { return Math.max(minimum, Math.min(maximum, value)); }
}
