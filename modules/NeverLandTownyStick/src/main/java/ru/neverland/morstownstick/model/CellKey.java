package ru.neverland.morstownstick.model;

import com.palmergames.bukkit.towny.object.WorldCoord;
import org.bukkit.World;

import java.util.List;

public record CellKey(String world, int x, int z) {
    public static CellKey from(WorldCoord coord) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return new CellKey(coord.getWorldName(), coord.getX(), coord.getZ());
    } }

    public WorldCoord worldCoord() { synchronized (ru.neverland.core.SuiteState.lock()) {
        return new WorldCoord(world, x, z);
    } }

    public List<CellKey> neighbors() { synchronized (ru.neverland.core.SuiteState.lock()) {
        return List.of(new CellKey(world, x + 1, z), new CellKey(world, x - 1, z),
                new CellKey(world, x, z + 1), new CellKey(world, x, z - 1));
    } }

    public int minBlockX(int cellSize) { synchronized (ru.neverland.core.SuiteState.lock()) { return x * cellSize; } }
    public int minBlockZ(int cellSize) { synchronized (ru.neverland.core.SuiteState.lock()) { return z * cellSize; } }

    public boolean sameWorld(World value) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return value != null && value.getName().equals(world);
    } }
}
