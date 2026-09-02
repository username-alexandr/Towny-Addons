package ru.neverland.townybuilds;

import org.bukkit.Material;
import ru.neverland.townybuilds.construction.BlueprintPlan;
import ru.neverland.townybuilds.construction.BuildingBlueprintGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class BlueprintLocalizationSmoke {
    public static void main(String[] args) {
        BuildingBlueprintGenerator generator = new BuildingBlueprintGenerator();
        Set<Material> used = new LinkedHashSet<>();
        for (String project : generator.supportedProjects()) {
            for (int level = 1; level <= generator.maximumStage(project); level++) {
                collect(used, generator.generate(project, level));
                BlueprintPlan legacy = generator.generateLegacy(project, level);
                if (legacy != null) collect(used, legacy);
            }
        }
        used.remove(Material.AIR);

        Map<String, String> names = loadNames(Path.of("src/main/resources/item-names.yml"));
        String missing = used.stream()
                .filter(material -> !names.containsKey(material.name()))
                .sorted(Comparator.comparing(Enum::name))
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        check(missing.isEmpty(), "Нет русских названий: " + missing);
        check("Белая терракота".equals(names.get(Material.WHITE_TERRACOTTA.name())),
                "WHITE_TERRACOTTA переведена неверно");

        System.out.println("BlueprintLocalizationSmoke OK: " + used.size() + " материалов");
    }

    private static void collect(Set<Material> materials, BlueprintPlan plan) {
        plan.blocks().values().forEach(block -> materials.add(block.material()));
    }

    private static Map<String, String> loadNames(Path path) {
        Map<String, String> names = new HashMap<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int separator = trimmed.indexOf(':');
                if (separator < 1) continue;
                String key = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                names.put(key, value);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось прочитать item-names.yml", exception);
        }
        return names;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
