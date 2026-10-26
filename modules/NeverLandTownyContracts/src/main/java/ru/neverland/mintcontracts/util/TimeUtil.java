package ru.neverland.mintcontracts.util;

public final class TimeUtil {
    private TimeUtil() {}
    public static String format(long seconds) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        long safe = Math.max(0, seconds), hours = safe / 3600, minutes = (safe % 3600) / 60;
        if (hours > 0) return hours + " ч " + minutes + " мин";
        return minutes + " мин " + (safe % 60) + " сек";
    } }
}
