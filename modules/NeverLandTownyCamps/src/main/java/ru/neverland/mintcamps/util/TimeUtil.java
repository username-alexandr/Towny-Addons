package ru.neverland.mintcamps.util;

public final class TimeUtil {
    private TimeUtil() {
    }

    public static String formatMillis(long millis) {
        return formatSeconds(Math.max(0L, (millis + 999L) / 1000L));
    }

    public static String formatSeconds(long seconds) {
        long value = Math.max(0L, seconds);
        long days = value / 86400L;
        long hours = value % 86400L / 3600L;
        long minutes = value % 3600L / 60L;
        long secs = value % 60L;
        StringBuilder text = new StringBuilder();
        if (days > 0) text.append(days).append(" д. ");
        if (hours > 0 || days > 0) text.append(hours).append(" ч. ");
        if (minutes > 0 || hours > 0 || days > 0) text.append(minutes).append(" мин. ");
        if (days == 0 && hours == 0) text.append(secs).append(" сек.");
        return text.toString().trim();
    }
}
