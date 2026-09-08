package ru.neverland.townybuilds.service;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townybuilds.util.ColorUtil;

import java.io.File;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class RussianItemNames {
    private final JavaPlugin plugin;
    private final Map<Material, String> names = new HashMap<>();

    public RussianItemNames(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        names.clear();
        try (InputStream stream = plugin.getResource("item-names.yml")) {
            if (stream != null) {
                InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                load(YamlConfiguration.loadConfiguration(reader));
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Не удалось загрузить встроенный словарь предметов: " + exception.getMessage());
        }
        File file = new File(plugin.getDataFolder(), "item-names.yml");
        if (!file.exists()) return;
        load(YamlConfiguration.loadConfiguration(file));
    }

    private void load(YamlConfiguration yaml) {
        for (String key : yaml.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material != null) {
                String name = yaml.getString(key, key);
                if (material == Material.DEEPSLATE_TILES && ("Глубинносланцевая плитка".equals(name) || "Глубинносланцевая плитка (полный блок)".equals(name))) {
                    name = "Глубинносланцевый кафель";
                }
                names.put(material, name);
            }
        }
    }

    public String name(ItemStack item) {
        if (item == null || item.getType().isAir()) return "Пусто";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasCustomName() && meta.customName() != null) {
            return ColorUtil.plain(meta.customName());
        }
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return ColorUtil.plain(meta.displayName());
        }
        return names.getOrDefault(item.getType(), fallback(item.getType()));
    }

    private String fallback(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(word);
        }
        if (result.isEmpty()) return material.name();
        result.setCharAt(0, Character.toUpperCase(result.charAt(0)));
        return result.toString();
    }
}
