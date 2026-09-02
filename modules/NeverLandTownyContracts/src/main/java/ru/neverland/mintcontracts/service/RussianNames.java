package ru.neverland.mintcontracts.service;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcontracts.util.ColorUtil;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class RussianNames {
    private final JavaPlugin plugin;
    private final Map<String, String> names = new LinkedHashMap<>();

    public RussianNames(JavaPlugin plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        names.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "item-names.yml"));
        for (String key : yaml.getKeys(false)) names.put(key.toUpperCase(Locale.ROOT), yaml.getString(key, key));
    }

    public String item(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return "предмет";
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return ColorUtil.strip(meta.getDisplayName());
        return value(stack.getType().name());
    }

    public String value(String key) {
        if (key == null || key.isBlank()) return "цель";
        String normalized = key.toUpperCase(Locale.ROOT);
        String translated = names.get(normalized);
        if (translated != null) return translated;
        String fallback = normalized.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(fallback.charAt(0)) + fallback.substring(1);
    }
}
