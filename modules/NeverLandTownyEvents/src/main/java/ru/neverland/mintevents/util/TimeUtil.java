package ru.neverland.mintevents.util;

public final class TimeUtil {
    private TimeUtil() {}

    public static String format(long seconds) {
        long safe = Math.max(0, seconds);
        long hours = safe / 3600;
        long minutes = (safe % 3600) / 60;
        long secs = safe % 60;
        if (hours > 0) return "%d ч %02d мин".formatted(hours, minutes);
        return "%d мин %02d сек".formatted(minutes, secs);
    }
}
