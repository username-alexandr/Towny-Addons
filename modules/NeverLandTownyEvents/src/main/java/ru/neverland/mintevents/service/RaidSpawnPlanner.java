package ru.neverland.mintevents.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure coordinate planning used by the raid spawn search and its smoke tests. */
public final class RaidSpawnPlanner {
    private RaidSpawnPlanner() { }

    public record Column(int x, int z) { }

    public static List<Integer> verticalCandidates(int highestY, int minHeight, int maxHeight,
                                                   int scanDown, int scanUp) {
        int base = highestY + 1;
        int down = Math.max(1, scanDown);
        int up = Math.max(1, scanUp);
        List<Integer> result = new ArrayList<>(down + up + 1);
        addY(result, base, minHeight, maxHeight);
        int distance = 1;
        while (distance <= down || distance <= up) {
            if (distance <= down) addY(result, base - distance, minHeight, maxHeight);
            if (distance <= up) addY(result, base + distance, minHeight, maxHeight);
            distance++;
        }
        return result;
    }

    public static List<Column> blockProbes(int minX, int maxX, int minZ, int maxZ) {
        int lowX = Math.min(minX, maxX);
        int highX = Math.max(minX, maxX);
        int lowZ = Math.min(minZ, maxZ);
        int highZ = Math.max(minZ, maxZ);
        int centerX = midpoint(lowX, highX);
        int centerZ = midpoint(lowZ, highZ);
        int quarterX = interpolate(lowX, highX, 1, 4);
        int threeQuarterX = interpolate(lowX, highX, 3, 4);
        int quarterZ = interpolate(lowZ, highZ, 1, 4);
        int threeQuarterZ = interpolate(lowZ, highZ, 3, 4);
        Set<Column> result = new LinkedHashSet<>();
        result.add(new Column(centerX, centerZ));
        result.add(new Column(quarterX, quarterZ));
        result.add(new Column(threeQuarterX, quarterZ));
        result.add(new Column(quarterX, threeQuarterZ));
        result.add(new Column(threeQuarterX, threeQuarterZ));
        result.add(new Column(centerX, quarterZ));
        result.add(new Column(centerX, threeQuarterZ));
        result.add(new Column(quarterX, centerZ));
        result.add(new Column(threeQuarterX, centerZ));
        return List.copyOf(result);
    }

    private static void addY(List<Integer> result, int y, int minHeight, int maxHeight) {
        if (y > minHeight && y + 1 < maxHeight && !result.contains(y)) result.add(y);
    }

    private static int midpoint(int low, int high) {
        return low + (high - low) / 2;
    }

    private static int interpolate(int low, int high, int numerator, int denominator) {
        return low + (high - low) * numerator / denominator;
    }
}
