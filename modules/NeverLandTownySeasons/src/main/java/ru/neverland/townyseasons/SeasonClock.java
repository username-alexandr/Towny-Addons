package ru.neverland.townyseasons;

/** Pure clock: wall time for CUSTOM, world game ticks for MINECRAFT. */
public final class SeasonClock {
    private SeasonClock() { }
    public record Date(Season season, long year, int day, long remaining) { }
    public static Date at(long elapsed, long dayLength, int days, Season first) {
        if (elapsed < 0 || dayLength < 1 || days < 1 || days > 365 || first == null)
            throw new IllegalArgumentException("Неверный календарь");
        long totalDays = elapsed / dayLength;
        long seasonIndex = totalDays / days;
        return new Date(Season.values()[(int)((seasonIndex % 4 + first.ordinal()) % 4)],
                seasonIndex / 4 + 1, (int)(totalDays % days) + 1,
                Math.subtractExact(Math.multiplyExact(days - totalDays % days, dayLength), elapsed % dayLength));
    }
}
