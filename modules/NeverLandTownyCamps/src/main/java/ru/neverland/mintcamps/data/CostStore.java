package ru.neverland.mintcamps.data;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class CostStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<Integer, List<ItemStack>> costs = new java.util.LinkedHashMap<>();

    public CostStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "upgrade-costs.yml");
        reload();
    }

    public void reload() {
        costs.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (int level = 1; level <= 3; level++) {
            List<ItemStack> items = new ArrayList<>();
            for (Object value : yaml.getList("costs." + level, Collections.emptyList())) {
                ItemStack item = parse(value);
                if (item != null && !item.getType().isAir() && item.getAmount() > 0) items.add(item);
            }
            costs.put(level, items);
        }
    }

    public List<ItemStack> get(int level) {
        return costs.getOrDefault(level, List.of()).stream().map(ItemStack::clone).toList();
    }

    public void set(int level, List<ItemStack> items) {
        List<ItemStack> clean = items.stream().filter(item -> item != null && !item.getType().isAir())
                .map(ItemStack::clone).toList();
        costs.put(Math.max(1, Math.min(3, level)), new ArrayList<>(clean));
        save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (int level = 1; level <= 3; level++) yaml.set("costs." + level, costs.getOrDefault(level, List.of()));
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Не удалось сохранить upgrade-costs.yml: " + exception.getMessage());
        }
    }

    private ItemStack parse(Object value) {
        if (value instanceof ItemStack item) return item.clone();
        if (!(value instanceof Map<?, ?> map)) return null;
        Object materialValue = map.containsKey("material") ? map.get("material") : map.get("type");
        Material material = Material.matchMaterial(String.valueOf(materialValue));
        if (material == null) return null;
        int amount = map.get("amount") instanceof Number number ? number.intValue() : 1;
        return new ItemStack(material, Math.max(1, amount));
    }
}
