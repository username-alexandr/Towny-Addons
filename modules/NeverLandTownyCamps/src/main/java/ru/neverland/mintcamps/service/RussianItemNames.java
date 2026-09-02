package ru.neverland.mintcamps.service;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcamps.util.ColorUtil;

import java.io.File;
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
        File file = new File(plugin.getDataFolder(), "item-names.yml");
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String key : yaml.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material != null) names.put(material, yaml.getString(key, key));
            }
        }
    }

    public String name(ItemStack item) {
        if (item == null) return "пусто";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasCustomName() && meta.customName() != null) {
            return ColorUtil.plain(meta.customName());
        }
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return ColorUtil.plain(meta.displayName());
        }
        return names.getOrDefault(item.getType(), fallback(item.getType()));
    }

    /** Клиентский официальный перевод покрывает каждый ванильный предмет текущей версии. */
    public Component component(ItemStack item) {
        if (item == null || item.getType().isAir()) return ColorUtil.component("пусто");
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasCustomName() && meta.customName() != null) return meta.customName();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) return meta.displayName();
        return Component.translatable(item.getType().translationKey());
    }

    private String fallback(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(word);
        }
        return ColorUtil.plain(result.toString());
    }
}
