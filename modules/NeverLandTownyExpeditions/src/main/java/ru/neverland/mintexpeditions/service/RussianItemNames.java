package ru.neverland.mintexpeditions.service;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintexpeditions.util.ColorUtil;

import java.io.File;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class RussianItemNames {
    private final JavaPlugin plugin;
    private final Map<Material, String> names = new EnumMap<>(Material.class);

    public RussianItemNames(JavaPlugin plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        names.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "item-names.yml"));
        for (String key : yaml.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material != null) names.put(material, yaml.getString(key, key));
        }
    }

    public String name(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return "пусто";
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return ColorUtil.strip(meta.getDisplayName());
        if (meta instanceof org.bukkit.inventory.meta.PotionMeta potion) {
            String name = PotionSupplies.name(potion.getBasePotionType());
            if (name != null) return name;
        }
        return names.getOrDefault(stack.getType(), fallback(stack.getType()));
    }

    private String fallback(Material material) {
        String value = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
