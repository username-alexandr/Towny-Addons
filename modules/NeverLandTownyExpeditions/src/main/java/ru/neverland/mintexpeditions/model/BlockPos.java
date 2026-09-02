package ru.neverland.mintexpeditions.model;
import org.bukkit.Location;
import org.bukkit.World;
public record BlockPos(int x, int y, int z) {
    public static BlockPos of(Location location) { return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()); }
    public Location location(World world) { return new Location(world, x, y, z); }
    public String key() { return x + "," + y + "," + z; }
    public static BlockPos parse(String raw) { String[] p = raw.split(",", 3); if (p.length != 3) throw new IllegalArgumentException(raw); return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])); }
    public BlockPos add(int dx, int dy, int dz) { return new BlockPos(x + dx, y + dy, z + dz); }
}
