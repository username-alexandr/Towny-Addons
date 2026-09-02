package ru.neverland.townychronicles.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());private TimeUtil(){}
    public static String date(long timestamp){return DATE.format(Instant.ofEpochMilli(timestamp));}
    public static long normalizedEpoch(long raw){if(raw<=0)return System.currentTimeMillis();return raw<10_000_000_000L?raw*1000L:raw;}
    public static long days(long since){return Math.max(0,(System.currentTimeMillis()-since)/86_400_000L);}
}
