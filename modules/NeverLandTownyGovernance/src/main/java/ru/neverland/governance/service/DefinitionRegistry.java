package ru.neverland.governance.service;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.governance.model.LawCategory;
import ru.neverland.governance.model.LawDefinition;
import ru.neverland.governance.model.LawEffects;
import ru.neverland.governance.model.OfficeDefinition;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DefinitionRegistry {
    private final JavaPlugin plugin;
    private final Map<String, LawDefinition> laws = new LinkedHashMap<>();
    private final Map<String, OfficeDefinition> offices = new LinkedHashMap<>();
    public DefinitionRegistry(JavaPlugin plugin) { this.plugin = plugin; reload(); }
    public void reload() { loadLaws(); loadOffices(); }
    public LawDefinition law(String id) { return id == null ? null : laws.get(id.toLowerCase(Locale.ROOT)); }
    public OfficeDefinition office(String id) { return id == null ? null : offices.get(id.toLowerCase(Locale.ROOT)); }
    public Collection<LawDefinition> laws() { return Collections.unmodifiableCollection(laws.values()); }
    public Collection<OfficeDefinition> offices() { return Collections.unmodifiableCollection(offices.values()); }

    private void loadLaws() {
        laws.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "laws.yml"));
        ConfigurationSection root = yaml.getConfigurationSection("laws");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            ConfigurationSection effects = section.getConfigurationSection("effects");
            Double tax = effects != null && effects.contains("tax-amount") ? effects.getDouble("tax-amount") : null;
            Boolean percentage = effects != null && effects.contains("tax-percentage") ? effects.getBoolean("tax-percentage") : null;
            LawEffects lawEffects = new LawEffects(tax, percentage,
                    number(effects, "construction-cost-multiplier", 1.0),
                    number(effects, "ideology-cost-multiplier", 1.0),
                    number(effects, "ideology-experience-multiplier", 1.0),
                    effects == null ? 0 : effects.getInt("bonus-claim-blocks", 0));
            String key = id.toLowerCase(Locale.ROOT);
            laws.put(key, new LawDefinition(key, section.getString("name", id),
                    LawCategory.parse(section.getString("category")), material(section.getString("material"), Material.PAPER),
                    section.getString("itemsadder-icon", ""), section.getString("exclusive-group", ""),
                    ListCopy.of(section.getStringList("description")), lawEffects));
        }
    }

    private void loadOffices() {
        offices.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "offices.yml"));
        ConfigurationSection root = yaml.getConfigurationSection("offices");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            Set<LawCategory> categories = EnumSet.noneOf(LawCategory.class);
            boolean all = false;
            for (String value : section.getStringList("proposal-categories")) {
                if (value.equalsIgnoreCase("ALL")) all = true; else categories.add(LawCategory.parse(value));
            }
            String key = id.toLowerCase(Locale.ROOT);
            offices.put(key, new OfficeDefinition(key, section.getString("name", id),
                    material(section.getString("material"), Material.PAPER), section.getString("itemsadder-icon", ""),
                    Math.max(1, section.getInt("max-holders", 1)), section.getBoolean("council-member", true),
                    Set.copyOf(categories), all, ListCopy.of(section.getStringList("description"))));
        }
    }

    private double number(ConfigurationSection section, String path, double fallback) { return section == null ? fallback : section.getDouble(path, fallback); }
    private Material material(String value, Material fallback) { Material material = value == null ? null : Material.matchMaterial(value); return material == null ? fallback : material; }

    private static final class ListCopy {
        private static java.util.List<String> of(java.util.List<String> values) { return java.util.List.copyOf(values); }
    }
}
