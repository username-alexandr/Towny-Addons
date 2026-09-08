package ru.neverland.mintexpeditions.service;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.localization.MaterialLabels;
import ru.neverland.localization.MaterialNameConfig;

public final class RussianItemNames {
    private final JavaPlugin plugin;
    private final MaterialLabels names = new MaterialLabels();

    public RussianItemNames(JavaPlugin plugin) { this.plugin = plugin; reload(); }

    public void reload() { MaterialNameConfig.reload(plugin, names); }

    public String name(Material material) {
        return material == null ? "Пусто" : names.name(material.name());
    }

    public String name(ItemStack item) {
        if (item == null || item.getType().isAir()) return "Пусто";
        ItemMeta meta = item.getItemMeta();
        String custom = MaterialNameConfig.customName(meta);
        if (custom != null) return custom;
        if (meta instanceof org.bukkit.inventory.meta.PotionMeta potion) {
            String name = PotionSupplies.name(potion.getBasePotionType());
            if (name != null) return name;
        }
        return name(item.getType());
    }
}
