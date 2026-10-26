package ru.neverland.mintcamps.model;

import org.bukkit.Location;
import org.bukkit.World;

public record BlockPos(int x, int y, int z) {
    public static BlockPos of(Location location) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    } }

    public Location location(World world) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        return new Location(world, x, y, z);
    } }

    public String key() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        return x + "," + y + "," + z;
    } }

    public static BlockPos parse(String value) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        String[] parts = value.split(",", 3);
        if (parts.length != 3) throw new IllegalArgumentException("Некорректная позиция: " + value);
        return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    } }
}
