package ru.neverland.mintcontracts.service;
import ru.neverland.localization.MaterialNameConfig;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcontracts.integration.ItemsAdderHook;
import ru.neverland.mintcontracts.model.ContractDefinition;
import ru.neverland.mintcontracts.model.ContractType;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ContractRegistry {
    private final JavaPlugin plugin;
    private final ItemsAdderHook itemsAdder;
    private final Map<String, ContractDefinition> templates = new LinkedHashMap<>();
    public ContractRegistry(JavaPlugin plugin, ItemsAdderHook itemsAdder) { this.plugin = plugin; this.itemsAdder = itemsAdder; reload(); }
    public void reload() {
        templates.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "contracts.yml"));
        ConfigurationSection root = yaml.getConfigurationSection("templates");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            try {
                ContractType type = ContractType.valueOf(section.getString("type", "DELIVERY").toUpperCase(Locale.ROOT));
                String target = section.getString("target", "").trim();
                if (!target.toLowerCase(Locale.ROOT).startsWith("itemsadder:")
                        && type != ContractType.MOB_KILL) {
                    target = ru.neverland.localization.MaterialLabels.canonicalKey(target);
                }
                ItemStack delivery = type == ContractType.DELIVERY ? targetItem(target) : null;
                if (type == ContractType.DELIVERY && delivery == null) throw new IllegalArgumentException("неизвестный предмет " + target);
                Material icon = MaterialNameConfig.matchMaterial(section.getString("icon", "PAPER"));
                if (icon == null) icon = Material.PAPER;
                ContractDefinition definition = new ContractDefinition(id.toLowerCase(Locale.ROOT),
                        section.getString("name", id), type, icon, section.getInt("slot", 28),
                        List.copyOf(section.getStringList("description")), target, delivery,
                        Math.max(1, section.getInt("goal", 1)), Math.max(0, section.getDouble("reward", 0)),
                        Math.max(300, section.getLong("duration-hours", 24) * 3600));
                templates.put(definition.id(), definition);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Шаблон " + id + " пропущен: " + exception.getMessage());
            }
        }
    }
    public ItemStack targetItem(String target) {
        if (target.toLowerCase(Locale.ROOT).startsWith("itemsadder:"))
            return itemsAdder.item(target.substring("itemsadder:".length()));
        Material material = MaterialNameConfig.matchMaterial(target);
        return material == null ? null : new ItemStack(material);
    }
    public ContractDefinition get(String id) { return id == null ? null : templates.get(id.toLowerCase(Locale.ROOT)); }
    public Collection<ContractDefinition> all() { return List.copyOf(templates.values()); }
}
