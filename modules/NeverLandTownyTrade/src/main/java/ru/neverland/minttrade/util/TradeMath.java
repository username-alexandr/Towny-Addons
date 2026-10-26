package ru.neverland.minttrade.util;

public final class TradeMath {
    private TradeMath() {}
    public static double cents(double value) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return Math.round(value * 100) / 100.0; } }
    public static double tariff(double basePrice, double percent) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return cents(basePrice * percent / 100.0); } }
    public static double progress(long departedAt, long arrivesAt, long now) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        long duration = Math.max(1, arrivesAt - departedAt);
        return Math.max(0, Math.min(1, (double) (now - departedAt) / duration));
    } }
}
