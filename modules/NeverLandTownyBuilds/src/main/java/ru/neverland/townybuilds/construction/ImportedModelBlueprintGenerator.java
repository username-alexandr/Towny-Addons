package ru.neverland.townybuilds.construction;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the reviewed 37-model expansion without a runtime JSON dependency.
 *
 * <p>The release model archive contains the original JSON sources. During the
 * build they are represented by compact {@code .nltb} resources containing the
 * same 25,497 coordinates, construction stages and door states.</p>
 */
public final class ImportedModelBlueprintGenerator {
    public static final List<String> PROJECTS = List.of(
            "sawmill", "quarry", "foundry", "mill", "bakery", "apiary",
            "warehouse", "caravanserai", "auction", "merchant_guild", "cargo_terminal",
            "water_tower", "sewer", "baths", "roads", "bridge_service", "reservoir",
            "guard", "arsenal", "armory", "watchtower", "fortress_gate", "counterintel",
            "alchemy", "archive", "cartography", "research", "botanical",
            "theater", "square", "arena", "gallery", "park", "guild_house",
            "memorial", "shelter", "cathedral"
    );

    public static final Map<String, Integer> SOURCE_BLOCKS = Map.ofEntries(
            Map.entry("sawmill", 522), Map.entry("quarry", 329), Map.entry("foundry", 574),
            Map.entry("mill", 628), Map.entry("bakery", 433), Map.entry("apiary", 415),
            Map.entry("warehouse", 800), Map.entry("caravanserai", 711), Map.entry("auction", 732),
            Map.entry("merchant_guild", 759), Map.entry("cargo_terminal", 1000),
            Map.entry("water_tower", 325), Map.entry("sewer", 586), Map.entry("baths", 781),
            Map.entry("roads", 115), Map.entry("bridge_service", 215), Map.entry("reservoir", 556),
            Map.entry("guard", 637), Map.entry("arsenal", 558), Map.entry("armory", 510),
            Map.entry("watchtower", 692), Map.entry("fortress_gate", 947),
            Map.entry("counterintel", 642), Map.entry("alchemy", 622), Map.entry("archive", 730),
            Map.entry("cartography", 816), Map.entry("research", 896), Map.entry("botanical", 1000),
            Map.entry("theater", 938), Map.entry("square", 366), Map.entry("arena", 962),
            Map.entry("gallery", 725), Map.entry("park", 578), Map.entry("guild_house", 1434),
            Map.entry("memorial", 361), Map.entry("shelter", 708), Map.entry("cathedral", 1894)
    );

    private static final Set<String> LINEAR_PROJECTS = Set.of("roads", "bridge_service");
    private static final String[] STAGES = {
            "Площадка и фундамент",
            "Каркас и основные стены",
            "Крыша и проходимый вход",
            "Рабочее оснащение",
            "Полная городская отделка"
    };
    private final Map<String, List<BlueprintPlan>> cache = new ConcurrentHashMap<>();

    public Set<String> supportedProjects() {
        return Set.copyOf(PROJECTS);
    }

    public boolean isLinear(String rawId) {
        return rawId != null && LINEAR_PROJECTS.contains(rawId.toLowerCase(Locale.ROOT));
    }

    public BlueprintPlan generate(String rawId, int rawLevel) {
        String id = rawId == null ? "" : rawId.toLowerCase(Locale.ROOT);
        if (!PROJECTS.contains(id)) return null;
        int level = Math.max(1, Math.min(5, rawLevel));
        return cache.computeIfAbsent(id, this::loadPlans).get(level - 1);
    }

    public String stageName(String rawId, int rawLevel) {
        int level = Math.max(1, Math.min(5, rawLevel));
        if (isLinear(rawId)) {
            return ("roads".equalsIgnoreCase(rawId) ? "Участок дороги " : "Участок моста ") + level;
        }
        return STAGES[level - 1];
    }

    private List<BlueprintPlan> loadPlans(String id) {
        List<EncodedBlock> source = loadBlocks(id);
        Integer expected = SOURCE_BLOCKS.get(id);
        if (expected == null || source.size() != expected) {
            throw new IllegalStateException("Модель " + id + ": ожидалось " + expected
                    + " блоков, прочитано " + source.size());
        }
        List<BlueprintPlan> plans = new ArrayList<>(5);
        for (int level = 1; level <= 5; level++) {
            Map<BlockOffset, BlueprintBlock> blocks = new LinkedHashMap<>();
            for (EncodedBlock encoded : source) {
                if (encoded.block().stage() > level) continue;
                BlueprintBlock previous = blocks.putIfAbsent(encoded.offset(), encoded.block());
                if (previous != null) {
                    throw new IllegalStateException("Модель " + id + ": повтор координаты " + encoded.offset());
                }
            }
            plans.add(new BlueprintPlan(id, level, stageName(id, level), blocks));
        }
        return List.copyOf(plans);
    }

    private List<EncodedBlock> loadBlocks(String id) {
        String resource = "blueprints/imported/" + id + ".nltb";
        InputStream stream = ImportedModelBlueprintGenerator.class.getClassLoader().getResourceAsStream(resource);
        if (stream == null) throw new IllegalStateException("Не найден встроенный чертёж " + resource);
        List<EncodedBlock> blocks = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.split(",", -1);
                if (parts.length != 10) {
                    throw new IllegalStateException(resource + ": строка " + number + " содержит "
                            + parts.length + " полей вместо 10");
                }
                try {
                    BlockOffset offset = new BlockOffset(integer(parts[0]), integer(parts[1]), integer(parts[2]));
                    int stage = integer(parts[3]);
                    Material material = Material.valueOf(parts[4]);
                    BlockRole role = BlockRole.valueOf(parts[5]);
                    BlockFace facing = enumValue(BlockFace.class, parts[6]);
                    Bisected.Half half = halfValue(parts[7]);
                    Door.Hinge hinge = enumValue(Door.Hinge.class, parts[8]);
                    BlueprintBlock block = new BlueprintBlock(material, role, stage, facing, Axis.Y, half, hinge);
                    blocks.add(new EncodedBlock(offset, block));
                } catch (RuntimeException exception) {
                    throw new IllegalStateException(resource + ": ошибка в строке " + number + ": "
                            + exception.getMessage(), exception);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось прочитать " + resource, exception);
        }
        return blocks;
    }

    private int integer(String value) {
        return Integer.parseInt(value);
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return "-".equals(value) || value.isBlank() ? null : Enum.valueOf(type, value);
    }

    private Bisected.Half halfValue(String value) {
        if ("LOWER".equals(value)) return Bisected.Half.BOTTOM;
        if ("UPPER".equals(value)) return Bisected.Half.TOP;
        return enumValue(Bisected.Half.class, value);
    }

    private record EncodedBlock(BlockOffset offset, BlueprintBlock block) { }
}
