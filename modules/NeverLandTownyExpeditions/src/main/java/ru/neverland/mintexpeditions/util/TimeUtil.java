package ru.neverland.mintexpeditions.util;
public final class TimeUtil { private TimeUtil() {} public static String format(long millis) { long seconds = Math.max(0, (millis + 999) / 1000); long h = seconds / 3600; seconds %= 3600; long m = seconds / 60; seconds %= 60; if (h > 0) return h + " ч " + m + " мин"; if (m > 0) return m + " мин " + seconds + " сек"; return seconds + " сек"; } }
