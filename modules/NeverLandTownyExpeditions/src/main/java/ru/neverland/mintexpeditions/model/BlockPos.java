package ru.neverland.mintexpeditions.model;
import org.bukkit.Location;
import org.bukkit.World;
public record BlockPos(int x, int y, int z) {
    public static BlockPos of(Location location) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()); } }
    public Location location(World world) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return new Location(world, x, y, z); } }
    public String key() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return x + "," + y + "," + z; } }
    public static BlockPos parse(String raw) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { String[] p = raw.split(",", 3); if (p.length != 3) throw new IllegalArgumentException(raw); return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])); } }
    public BlockPos add(int dx, int dy, int dz) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return new BlockPos(x + dx, y + dy, z + dz); } }
}
