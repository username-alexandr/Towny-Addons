package ru.neverland.townyelections;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
public record ElectionsSettings(long interval, long initialDelay, long nomination, long voting, long checkTicks,
        double quorum, boolean authoritarian, Map<String,Integer> seats, Map<String,String> names) {
    public static ElectionsSettings load(ConfigurationSection y) {
        long interval = integer(y, "schedule.interval-days", 1, 3650) * 86_400_000L;
        long initial = integer(y, "schedule.first-election-delay-days", 0, 3650) * 86_400_000L;
        long nomination = integer(y, "schedule.nomination-hours", 1, 8760) * 3_600_000L;
        long voting = integer(y, "schedule.voting-hours", 1, 8760) * 3_600_000L;
        if (interval <= nomination + voting) throw new IllegalArgumentException("Интервал должен быть больше длительности кампании.");
        Object raw = y.get("quorum"); if (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < 0 || n.doubleValue() > 1) throw new IllegalArgumentException("quorum: 0..1");
        if (!(y.get("allow-authoritarian") instanceof Boolean)) throw new IllegalArgumentException("allow-authoritarian: true/false");
        var seats = new LinkedHashMap<String,Integer>(); var names = new LinkedHashMap<String,String>();
        var root = y.getConfigurationSection("seats"); if (root == null) throw new IllegalArgumentException("Нет seats");
        for (String id : root.getKeys(false)) {
            if (!id.matches("[a-z][a-z0-9_]{0,39}")) throw new IllegalArgumentException("Неверная должность");
            seats.put(id, (int)integer(root, id, 1, 20)); names.put(id, y.getString("names." + id, id));
        }
        if (!Integer.valueOf(1).equals(seats.get("mayor")) || !seats.containsKey("councillor") || seats.size() > 20) throw new IllegalArgumentException("Нужны mayor: 1 и councillor; не более 20 должностей");
        return new ElectionsSettings(interval, initial, nomination, voting, integer(y,"schedule.check-seconds",1,3600)*20, n.doubleValue(), y.getBoolean("allow-authoritarian"), Collections.unmodifiableMap(seats), Map.copyOf(names));
    }
    static long integer(ConfigurationSection y, String key, long min, long max) {
        Object v = y.get(key); if (!(v instanceof Integer || v instanceof Long) || ((Number)v).longValue() < min || ((Number)v).longValue() > max) throw new IllegalArgumentException("Неверное целое значение: " + key);
        return ((Number)v).longValue();
    }
}
