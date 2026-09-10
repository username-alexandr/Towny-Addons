package ru.neverland.townybuilds;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townybuilds.construction.BuildingBlueprintGenerator;
import ru.neverland.townybuilds.service.ConstructionSupply;
import ru.neverland.townybuilds.service.ResourceBudget;

public final class ResourceBalanceSmoke {
    public static void main(String[] args) throws Exception {
        var generator = new BuildingBlueprintGenerator();
        var projects = yaml("projects.yml");
        var names = yaml("item-names.yml");
        List<String> rows = new ArrayList<>();
        rows.add("\uFEFFКатегория;Проект;Название;Уровень;Материал;Предмет;Было;Стало");
        Set<String> missing = new TreeSet<>();
        int count = 0, levels = 0;
        for (String group : List.of("buildings", "wonders")) {
            var root = projects.getConfigurationSection(group);
            for (String id : root.getKeys(false)) {
                count++;
                var section = root.getConfigurationSection(id);
                var levelRoot = section.getConfigurationSection("levels");
                for (String levelKey : levelRoot.getKeys(false)) {
                    levels++;
                    int level = Integer.parseInt(levelKey);
                    List<ResourceBudget.Cost> before = new ArrayList<>();
                    for (String raw : levelRoot.getStringList(levelKey + ".resources")) {
                        int split = raw.lastIndexOf(':');
                        Material material = Material.matchMaterial(raw.substring(0, split));
                        check(material != null, "Unknown resource " + raw);
                        material = ConstructionSupply.material(material);
                        before.add(new ResourceBudget.Cost(material.name(), Integer.parseInt(raw.substring(split + 1))));
                    }
                    var after = ResourceBudget.balance(generator, id, level, group.equals("wonders"), before);
                    check(after.size() == before.size(), "Lost resources: " + id);
                    check(after.equals(ResourceBudget.balance(generator, id, level, group.equals("wonders"), before)), "Non-deterministic budget");
                    for (int i = 0; i < after.size(); i++) {
                        var cost = after.get(i);
                        check(cost.amount() > 0, "Non-positive amount: " + id);
                        if (!names.contains(cost.material())) missing.add(cost.material());
                        String title = section.getString("name", id).replaceAll("(?i)&#[a-f0-9]{6}|&[0-9a-fk-or]", "");
                        rows.add(group + ";" + id + ";" + title + ";" + level + ";" + cost.material()
                                + ";" + names.getString(cost.material(), cost.material()) + ";" + before.get(i).amount() + ";" + cost.amount());
                    }
                    for (var block : generator.generate(id, level).blocks().values()) {
                        String material = ConstructionSupply.material(block.material()).name();
                        if (!names.contains(material)) missing.add(material);
                    }
                }
            }
        }
        check(count == 94, "Expected all 94 projects, got " + count);
        check(missing.isEmpty(), "Missing Russian material names: " + missing);
        check(ResourceBudget.scale(List.of(new ResourceBudget.Cost("STONE", 100)), 115).get(0).amount() == 120, "Budget rounding");
        check(ResourceBudget.weight("DIAMOND_BLOCK") > ResourceBudget.weight("STONE"), "Rare material weight");
        Path output = Path.of("build/reports/resource-balance.csv");
        Files.createDirectories(output.getParent());
        Files.write(output, rows, StandardCharsets.UTF_8);
        System.out.println("ResourceBalanceSmoke OK: " + count + " projects, " + levels + " levels; " + output);
    }
    private static YamlConfiguration yaml(String name) throws Exception {
        try (var stream = java.util.Objects.requireNonNull(ResourceBalanceSmoke.class.getResourceAsStream("/" + name));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
