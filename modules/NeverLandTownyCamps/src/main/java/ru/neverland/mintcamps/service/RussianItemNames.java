package ru.neverland.mintcamps.service;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.localization.MaterialLabels;
import ru.neverland.localization.MaterialNameConfig;
import ru.neverland.mintcamps.util.ColorUtil;

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
        return name(item.getType());
    }

    public net.kyori.adventure.text.Component component(ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasCustomName() && meta.customName() != null) return meta.customName();
            if (meta != null && meta.hasDisplayName() && meta.displayName() != null) return meta.displayName();
        }
        return ColorUtil.component(name(item));
    }
}
