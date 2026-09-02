package ru.neverland.mintevents;

import ru.neverland.mintevents.service.RaidSpawnPlanner;

import java.util.HashSet;
import java.util.List;

public final class RaidSpawnPlannerSmoke {
    public static void main(String[] args) {
        List<Integer> vertical = RaidSpawnPlanner.verticalCandidates(64, -64, 320, 3, 2);
        if (!vertical.equals(List.of(65, 64, 66, 63, 67, 62))) {
            throw new AssertionError("Неверный вертикальный каскад: " + vertical);
        }
        List<RaidSpawnPlanner.Column> normal = RaidSpawnPlanner.blockProbes(1, 14, 1, 14);
        if (normal.size() != 9 || new HashSet<>(normal).size() != normal.size()) {
            throw new AssertionError("Неверная сетка участка: " + normal);
        }
        for (RaidSpawnPlanner.Column column : normal) {
            if (column.x() < 1 || column.x() > 14 || column.z() < 1 || column.z() > 14) {
                throw new AssertionError("Точка вышла за участок: " + column);
            }
        }
        List<RaidSpawnPlanner.Column> tiny = RaidSpawnPlanner.blockProbes(5, 5, 7, 7);
        if (!tiny.equals(List.of(new RaidSpawnPlanner.Column(5, 7)))) {
            throw new AssertionError("Неверная обработка маленького участка: " + tiny);
        }
        System.out.println("RaidSpawnPlannerSmoke OK");
    }
}
