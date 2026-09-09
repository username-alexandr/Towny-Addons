package ru.neverland.townybuilds.army;
import java.util.OptionalInt;
public final class MobilizationPolicy {
    private MobilizationPolicy() {}
    public enum Result { ALLOWED, FORBIDDEN, NO_BUILDING, FOREIGN_RESIDENT, UNKNOWN_AGE, UNDER_AGE, ALREADY_MOBILIZED, FULL }
    public static Result check(boolean manager, int level, boolean sameTown, OptionalInt age, boolean enrolled, int size, int capacity) {
        if (!manager) return Result.FORBIDDEN;
        if (level <= 0) return Result.NO_BUILDING;
        if (!sameTown) return Result.FOREIGN_RESIDENT;
        if (age.isEmpty()) return Result.UNKNOWN_AGE;
        if (age.getAsInt() < 18) return Result.UNDER_AGE;
        if (enrolled) return Result.ALREADY_MOBILIZED;
        if (size >= capacity) return Result.FULL;
        return Result.ALLOWED;
    }
    public static OptionalInt parseAge(String text) {
        if (text == null || !text.trim().matches("[0-9]{1,3}")) return OptionalInt.empty();
        int value = Integer.parseInt(text.trim());
        return value <= 150 ? OptionalInt.of(value) : OptionalInt.empty();
    }
}
