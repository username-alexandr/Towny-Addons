package ru.neverland.mintcontracts.fishing;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import ru.neverland.core.SuiteScheduler;

/** Reads only loaded blocks owned by the current Folia region; never loads a neighbouring chunk. */
final class FishingHabitat {
    private FishingHabitat() { }

    static WaterSurface.Result inspect(Location hook, FishingSettings settings) {
        World world = hook.getWorld();
        if (world == null || !SuiteScheduler.owns(hook)) return WaterSurface.Result.UNKNOWN;
        int ox = hook.getBlockX(), oz = hook.getBlockZ(), y = hook.getBlockY();
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return WaterSurface.Result.SMALL;
        if (world.getBlockAt(ox, y, oz).getType() != Material.WATER) {
            if (y <= world.getMinHeight() || world.getBlockAt(ox, y - 1, oz).getType() != Material.WATER)
                return WaterSurface.Result.SMALL;
            y--;
        }
        int surfaceY = y;
        Location sample = hook.clone();
        var surface = WaterSurface.inspect(ox, oz, settings.scanRadius(), settings.minimumSurface(), settings.minimumSpan(), (x, z) -> {
            sample.setX(x + .5); sample.setY(surfaceY + .5); sample.setZ(z + .5);
            if (!SuiteScheduler.owns(sample) || !world.isChunkLoaded(x >> 4, z >> 4)) return WaterSurface.Cell.UNKNOWN;
            return world.getBlockAt(x, surfaceY, z).getType() == Material.WATER
                    ? WaterSurface.Cell.WATER : WaterSurface.Cell.OTHER;
        });
        if (surface != WaterSurface.Result.LARGE) return surface;
        for (int depth = 1; depth < settings.minimumDepth(); depth++) {
            if (surfaceY - depth < world.getMinHeight() || world.getBlockAt(ox, surfaceY - depth, oz).getType() != Material.WATER)
                return WaterSurface.Result.SHALLOW;
        }
        return WaterSurface.Result.LARGE;
    }
}
