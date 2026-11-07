package ru.neverland.morstownstick.integration;

import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.object.WorldCoord;
import com.palmergames.bukkit.util.BiomeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.SuiteScheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Towny's biome filter reads every block of every proposed plot. On Folia these
 * reads must run in the region owning the blocks, not on the claimant's region.
 * No Towny state is changed while the samples are collected.
 */
public final class ClaimBiomePreflight {
    public record Verdict(Set<WorldCoord> allowed, boolean unwantedEnabled, boolean oceanEnabled,
                          double unwantedThreshold, double oceanThreshold, Set<String> unwantedNames) {
        public boolean isCurrent() {
            return unwantedEnabled == TownySettings.isUnwantedBiomeClaimingEnabled()
                && oceanEnabled == TownySettings.isOceanClaimingBlocked()
                && Double.compare(unwantedThreshold, TownySettings.getUnwantedBiomeThreshold()) == 0
                && Double.compare(oceanThreshold, TownySettings.getOceanBlockThreshold()) == 0
                && unwantedNames.equals(Set.copyOf(TownySettings.getUnwantedBiomeNames()));
        }
    }
    private record Sample(WorldCoord cell, World world, int chunkX, int chunkZ,
                          int fromX, int toX, int fromZ, int toZ) { }
    private record Count(WorldCoord cell, int total, int unwanted, int ocean) {
        Count plus(Count other) {
            return new Count(cell, Math.addExact(total, other.total),
                Math.addExact(unwanted, other.unwanted), Math.addExact(ocean, other.ocean));
        }
    }
    private final JavaPlugin plugin;

    public ClaimBiomePreflight(JavaPlugin plugin) { this.plugin = plugin; }

    public CompletionStage<Verdict> check(List<WorldCoord> requested) {
        boolean unwantedEnabled = TownySettings.isUnwantedBiomeClaimingEnabled();
        boolean oceanEnabled = TownySettings.isOceanClaimingBlocked();
        double unwantedThreshold = TownySettings.getUnwantedBiomeThreshold();
        double oceanThreshold = TownySettings.getOceanBlockThreshold();
        Set<String> names = Set.copyOf(TownySettings.getUnwantedBiomeNames());
        if (!unwantedEnabled && !oceanEnabled)
            return CompletableFuture.completedFuture(new Verdict(Set.copyOf(requested), false, false,
                unwantedThreshold, oceanThreshold, names));

        int size = TownySettings.getTownBlockSize();
        if (size < 1 || (long) size * size * requested.size() > 16384)
            throw new IllegalStateException("Для проверки биомов разделите план на меньшие части");
        Set<NamespacedKey> unwantedKeys = new HashSet<>();
        if (unwantedEnabled)
            for (String name : names) {
                NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
                if (key != null) unwantedKeys.add(key);
            }
        Set<NamespacedKey> keys = Set.copyOf(unwantedKeys);
        List<CompletableFuture<Count>> pending = new ArrayList<>();
        for (WorldCoord cell : requested) {
            World world = cell.getBukkitWorld();
            if (world == null) throw new IllegalStateException("Мир выбранного участка недоступен");
            int startX = Math.multiplyExact(cell.getX(), size), startZ = Math.multiplyExact(cell.getZ(), size);
            int endX = Math.addExact(startX, size - 1), endZ = Math.addExact(startZ, size - 1);
            for (int cz = Math.floorDiv(startZ, 16); cz <= Math.floorDiv(endZ, 16); cz++)
                for (int cx = Math.floorDiv(startX, 16); cx <= Math.floorDiv(endX, 16); cx++) {
                    Sample sample = new Sample(cell, world, cx, cz,
                        Math.max(startX, cx * 16), Math.min(endX, cx * 16 + 15),
                        Math.max(startZ, cz * 16), Math.min(endZ, cz * 16 + 15));
                    CompletableFuture<Count> result = new CompletableFuture<>();
                    pending.add(result);
                    try {
                        SuiteScheduler.region(plugin, new Location(world, cx * 16 + 8, world.getMinHeight(), cz * 16 + 8),
                            () -> {
                                try {
                                    if (!Bukkit.isOwnedByCurrentRegion(world, sample.chunkX(), sample.chunkZ()))
                                        throw new IllegalStateException("Регион выбранного участка недоступен");
                                    result.complete(sample(sample, unwantedEnabled, oceanEnabled, keys));
                                } catch (Throwable error) { result.completeExceptionally(error); }
                            });
                    } catch (Throwable error) { result.completeExceptionally(error); }
                }
        }
        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            Map<WorldCoord, Count> counts = new HashMap<>();
            for (CompletableFuture<Count> future : pending) {
                Count value = future.join();
                counts.merge(value.cell(), value, Count::plus);
            }
            Set<WorldCoord> allowed = new HashSet<>();
            for (WorldCoord cell : requested) {
                Count value = counts.get(cell);
                if (value == null || value.total() != size * size)
                    throw new IllegalStateException("Не удалось полностью проверить биомы участка");
                if ((!unwantedEnabled || belowThreshold(value.unwanted(), value.total(), unwantedThreshold))
                    && (!oceanEnabled || belowThreshold(value.ocean(), value.total(), oceanThreshold)))
                    allowed.add(cell);
            }
            return new Verdict(Set.copyOf(allowed), unwantedEnabled, oceanEnabled,
                unwantedThreshold, oceanThreshold, names);
        }).orTimeout(30, java.util.concurrent.TimeUnit.SECONDS);
    }

    static boolean belowThreshold(int bad, int total, double threshold) {
        return (double) bad / total < threshold;
    }

    private static Count sample(Sample s, boolean unwantedEnabled, boolean oceanEnabled,
                                Set<NamespacedKey> unwantedKeys) {
        int total = 0, unwanted = 0, ocean = 0;
        for (int z = s.fromZ(); z <= s.toZ(); z++)
            for (int x = s.fromX(); x <= s.toX(); x++) {
                int y = s.world().getHighestBlockYAt(x, z);
                NamespacedKey key = BiomeUtil.getBiomeKey(s.world(), x, y, z);
                total++;
                if (unwantedEnabled && unwantedKeys.contains(key)) unwanted++;
                if (oceanEnabled && key.getKey().contains("ocean")) ocean++;
            }
        return new Count(s.cell(), total, unwanted, ocean);
    }
}
