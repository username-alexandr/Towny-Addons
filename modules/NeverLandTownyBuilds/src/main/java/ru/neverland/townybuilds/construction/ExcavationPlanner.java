package ru.neverland.townybuilds.construction;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Plans safe terrain removal for blueprint layers below the selected ground level. */
public final class ExcavationPlanner {
    private static final Set<Material> NATURAL_TERRAIN = Set.of(
            Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT,
            Material.PODZOL, Material.MYCELIUM, Material.DIRT_PATH, Material.MUD,
            Material.PACKED_MUD, Material.CLAY, Material.GRAVEL, Material.SAND,
            Material.RED_SAND, Material.STONE, Material.DEEPSLATE, Material.GRANITE,
            Material.DIORITE, Material.ANDESITE, Material.TUFF, Material.CALCITE,
            Material.DRIPSTONE_BLOCK, Material.MOSS_BLOCK, Material.SNOW_BLOCK,
            Material.WATER, Material.LAVA, Material.SNOW, Material.VINE,
            Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN,
            Material.LARGE_FERN, Material.DEAD_BUSH
    );

    private ExcavationPlanner() { }

    public static Set<BlockOffset> offsets(BlueprintPlan plan, int fromStage) {
        Map<Integer, Bounds> layers = new LinkedHashMap<>();
        for (Map.Entry<BlockOffset, BlueprintBlock> entry : plan.blocks().entrySet()) {
            BlockOffset offset = entry.getKey();
            if (offset.y() >= 0 || entry.getValue().stage() < fromStage) continue;
            layers.computeIfAbsent(offset.y(), ignored -> new Bounds()).include(offset.x(), offset.z());
        }

        Set<BlockOffset> result = new LinkedHashSet<>();
        for (Map.Entry<Integer, Bounds> layer : layers.entrySet()) {
            Bounds bounds = layer.getValue();
            for (int x = bounds.minX; x <= bounds.maxX; x++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    result.add(new BlockOffset(x, layer.getKey(), z));
                }
            }
        }
        return Set.copyOf(result);
    }

    public static boolean canClear(Material material) {
        if (material == null || material == Material.AIR
                || material == Material.CAVE_AIR || material == Material.VOID_AIR) return true;
        return NATURAL_TERRAIN.contains(material) || material.name().endsWith("_ORE");
    }

    private static final class Bounds {
        private int minX = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        private void include(int x, int z) {
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
    }
}
