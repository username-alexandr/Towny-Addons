package ru.neverland.mintcontracts.service;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.localization.MaterialLabels;
import ru.neverland.localization.MaterialNameConfig;

public final class RussianNames {
    private final JavaPlugin plugin;
    private final MaterialLabels names = new MaterialLabels();

    public RussianNames(JavaPlugin plugin) { this.plugin = plugin; reload(); }

    public void reload() { MaterialNameConfig.reload(plugin, names); }

    public String item(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return "Пусто";
        ItemMeta meta = stack.getItemMeta();
        String custom = MaterialNameConfig.customName(meta);
        if (custom != null) return custom;
        return value(stack.getType().name());
    }

    public String value(String key) {
        if (key == null || key.isBlank()) return "Цель";
        return names.name(key);
    }
}
