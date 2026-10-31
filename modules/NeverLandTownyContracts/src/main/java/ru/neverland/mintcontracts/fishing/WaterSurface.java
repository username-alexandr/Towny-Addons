package ru.neverland.mintcontracts.fishing;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Bounded, four-connected water surface around the hook; provenance is irrelevant. */
public final class WaterSurface {
    private WaterSurface() { }

    public enum Cell { WATER, OTHER, UNKNOWN }
    public enum Result { LARGE, SMALL, SHALLOW, UNKNOWN }

    @FunctionalInterface public interface Source { Cell at(int x, int z); }

    public static Result inspect(int originX, int originZ, int radius, int minimumCells, int minimumSpan, Source source) {
        if (radius < 1 || radius > 16 || minimumCells < 1 || minimumCells > (radius * 2 + 1) * (radius * 2 + 1)
                || minimumSpan < 1 || minimumSpan > radius * 2 + 1 || source == null)
            throw new IllegalArgumentException("Неверные размеры проверки водоёма");
        var queue = new ArrayDeque<Long>();
        Set<Long> seen = new HashSet<>();
        long origin = key(originX, originZ);
        seen.add(origin);
        queue.add(origin);
        int cells = 0, minX = originX, maxX = originX, minZ = originZ, maxZ = originZ;
        boolean unknown = false;
        while (!queue.isEmpty()) {
            long current = queue.removeFirst();
            int x = (int) (current >> 32), z = (int) current;
            Cell cell = source.at(x, z);
            if (cell == Cell.UNKNOWN) { unknown = true; continue; }
            if (cell != Cell.WATER) continue;
            cells++;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            if (cells >= minimumCells && maxX - minX + 1 >= minimumSpan && maxZ - minZ + 1 >= minimumSpan)
                return Result.LARGE;
            if (x > originX - radius) add(queue, seen, x - 1, z);
            if (x < originX + radius) add(queue, seen, x + 1, z);
            if (z > originZ - radius) add(queue, seen, x, z - 1);
            if (z < originZ + radius) add(queue, seen, x, z + 1);
        }
        return unknown ? Result.UNKNOWN : Result.SMALL;
    }

    private static void add(ArrayDeque<Long> queue, Set<Long> seen, int x, int z) {
        long key = key(x, z);
        if (seen.add(key)) queue.addLast(key);
    }

    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }
}
