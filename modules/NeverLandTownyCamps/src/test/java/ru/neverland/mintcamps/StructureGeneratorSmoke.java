package ru.neverland.mintcamps;

import org.bukkit.Material;
import ru.neverland.mintcamps.model.BlockPos;
import ru.neverland.mintcamps.model.Placement;
import ru.neverland.mintcamps.model.StyleDefinition;
import ru.neverland.mintcamps.service.StructureGenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StructureGeneratorSmoke {
    public static void main(String[] args) {
        Map<String, Material> palette = new HashMap<>();
        palette.put("log", Material.OAK_LOG);
        palette.put("stripped-log", Material.STRIPPED_OAK_LOG);
        palette.put("planks", Material.OAK_PLANKS);
        palette.put("slab", Material.OAK_SLAB);
        palette.put("stairs", Material.OAK_STAIRS);
        palette.put("fence", Material.OAK_FENCE);
        palette.put("wool", Material.GREEN_WOOL);
        palette.put("carpet", Material.GREEN_CARPET);
        palette.put("foundation", Material.STONE_BRICKS);
        palette.put("stone-ring", Material.COBBLESTONE_SLAB);
        palette.put("light", Material.LANTERN);
        palette.put("banner", Material.GREEN_BANNER);
        palette.put("bed", Material.GREEN_BED);
        StyleDefinition style = new StyleDefinition("plains", "Равнины", palette);
        StructureGenerator generator = new StructureGenerator();
        int previous = 0;
        for (int level = 1; level <= 3; level++) {
            List<Placement> placements = generator.generate(level, style);
            require(placements.size() > previous, "уровень " + level + " не расширяет постройку");
            require(placements.stream().allMatch(value -> value.material() != null), "найден пустой материал");
            require(placements.stream().anyMatch(value -> value.material() == Material.CAMPFIRE
                    && value.relative().equals(StructureGenerator.CAMPFIRE_RELATIVE)),
                    "костёр находится выше уровня земли");
            BlockPos stash = generator.stashRelative(level);
            require(placements.stream().anyMatch(value -> value.relative().equals(stash)
                    && value.material() == Material.BARREL), "нет бочки уровня " + level);
            previous = placements.size();
            System.out.println("level=" + level + " blocks=" + placements.size());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
