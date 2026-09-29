package ru.neverland.minttrade.util;

public final class TradeMath {
    private TradeMath() {}
    public static double cents(double value) { synchronized (ru.neverland.core.SuiteState.lock()) { return Math.round(value * 100) / 100.0; } }
    public static double tariff(double basePrice, double percent) { synchronized (ru.neverland.core.SuiteState.lock()) { return cents(basePrice * percent / 100.0); } }
    public static double progress(long departedAt, long arrivesAt, long now) { synchronized (ru.neverland.core.SuiteState.lock()) {
        long duration = Math.max(1, arrivesAt - departedAt);
        return Math.max(0, Math.min(1, (double) (now - departedAt) / duration));
    } }
}
