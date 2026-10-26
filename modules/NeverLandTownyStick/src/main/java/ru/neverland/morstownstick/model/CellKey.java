package ru.neverland.morstownstick.model;

import com.palmergames.bukkit.towny.object.WorldCoord;
import org.bukkit.World;

import java.util.List;

public record CellKey(String world, int x, int z) {
    public static CellKey from(WorldCoord coord) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        return new CellKey(coord.getWorldName(), coord.getX(), coord.getZ());
    } }

    public WorldCoord worldCoord() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        return new WorldCoord(world, x, z);
    } }

    public List<CellKey> neighbors() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        return List.of(new CellKey(world, x + 1, z), new CellKey(world, x - 1, z),
                new CellKey(world, x, z + 1), new CellKey(world, x, z - 1));
    } }

    public int minBlockX(int cellSize) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return x * cellSize; } }
    public int minBlockZ(int cellSize) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return z * cellSize; } }

    public boolean sameWorld(World value) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
        return value != null && value.getName().equals(world);
    } }
}
