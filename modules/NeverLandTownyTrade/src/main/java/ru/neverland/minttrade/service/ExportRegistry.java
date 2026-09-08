package ru.neverland.minttrade.service;
import ru.neverland.localization.MaterialNameConfig;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.minttrade.integration.ItemsAdderHook;
import ru.neverland.minttrade.model.ExportDefinition;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ExportRegistry {
    private final JavaPlugin plugin;
    private final ru.neverland.localization.MaterialLabels itemNames = new ru.neverland.localization.MaterialLabels();
    private final ItemsAdderHook itemsAdder;
    private final Map<String, ExportDefinition> exports = new LinkedHashMap<>();
    public ExportRegistry(JavaPlugin plugin, ItemsAdderHook itemsAdder) { this.plugin = plugin; this.itemsAdder = itemsAdder; reload(); }
    public void reload() {
        exports.clear();
        MaterialNameConfig.reload(plugin, itemNames);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "exports.yml"));
        ConfigurationSection root = yaml.getConfigurationSection("exports");
        if (root == null) return;
        for (String rawId : root.getKeys(false)) {
            String path = "exports." + rawId + ".";
            String key = yaml.getString(path + "item", "STONE");
            ItemStack item = parseItem(key);
            Material icon = MaterialNameConfig.matchMaterial(yaml.getString(path + "icon", "CHEST"));
            if (item == null) { plugin.getLogger().warning("Пропущен экспорт " + rawId + ": предмет " + key + " не найден."); continue; }
            if (icon == null) icon = item.getType();
            String id = rawId.toLowerCase(Locale.ROOT);
            String configuredName = yaml.getString(path + "name");
            String displayName = configuredName == null ? itemName(item)
                    : itemNames.configuredName(item.getType().name(), configuredName);
            exports.put(id, new ExportDefinition(id, displayName, icon,
                    yaml.getInt(path + "slot", 28), yaml.getStringList(path + "description"), key,
                    item, Math.max(1, yaml.getInt(path + "amount", 64)), Math.max(0, yaml.getDouble(path + "price", 100))));
        }
    }
    public String itemName(ItemStack item) {
        if (item == null || item.getType().isAir()) return "Пусто";
        String custom = MaterialNameConfig.customName(item.getItemMeta());
        return custom == null ? itemNames.name(item.getType().name()) : custom;
    }
    private ItemStack parseItem(String key) {
        ItemStack custom = itemsAdder.item(key);
        if (custom != null) { custom.setAmount(1); return custom; }
        Material material = MaterialNameConfig.matchMaterial(key);
        return material == null ? null : new ItemStack(material);
    }
    public ExportDefinition get(String id) { return id == null ? null : exports.get(id.toLowerCase(Locale.ROOT)); }
    public Collection<ExportDefinition> all() { return exports.values(); }
}
