package ru.neverland.governance.util;

public final class TimeUtil {
    private TimeUtil() { }
    public static String format(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60; seconds %= 60;
        if (days > 0) return days + " д. " + hours + " ч.";
        if (hours > 0) return hours + " ч. " + minutes + " мин.";
        if (minutes > 0) return minutes + " мин. " + seconds + " сек.";
        return seconds + " сек.";
    }
}
