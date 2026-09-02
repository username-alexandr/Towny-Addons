package ru.neverland.mintevents.service;

import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintevents.model.ContributionRule;
import ru.neverland.mintevents.model.EventDefinition;
import ru.neverland.mintevents.model.EventMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EventRegistry {
    private final JavaPlugin plugin;
    private final Map<String, EventDefinition> definitions = new LinkedHashMap<>();

    public EventRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        definitions.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "events.yml"));
        ConfigurationSection root = yaml.getConfigurationSection("events");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            try {
                EventDefinition definition = parse(id.toLowerCase(Locale.ROOT), section);
                definitions.put(definition.id(), definition);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Событие " + id + " пропущено: " + exception.getMessage());
            }
        }
    }

    private EventDefinition parse(String id, ConfigurationSection section) {
        Material icon = material(section.getString("icon", "PAPER"), Material.PAPER);
        EventMode mode = EventMode.valueOf(section.getString("mode", id).toUpperCase(Locale.ROOT));
        BarColor barColor;
        try {
            barColor = BarColor.valueOf(section.getString("bossbar-color", "PURPLE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            barColor = BarColor.PURPLE;
        }
        List<ContributionRule> rules = new ArrayList<>();
        ConfigurationSection contributions = section.getConfigurationSection("contributions");
        if (contributions != null) {
            for (String key : contributions.getKeys(false)) {
                Material contributionMaterial = specialMaterial(key);
                if (contributionMaterial == null) {
                    plugin.getLogger().warning("Неизвестный предмет " + key + " в событии " + id);
                    continue;
                }
                int points = Math.max(1, contributions.getInt(key + ".points", 1));
                String name = contributions.getString(key + ".name", prettify(key));
                rules.add(new ContributionRule(key, contributionMaterial, points, name));
            }
        }
        if (rules.isEmpty()) throw new IllegalArgumentException("нет допустимых вкладов");
        return new EventDefinition(
                id,
                section.getString("name", id),
                icon,
                section.getString("description", ""),
                Math.max(60, section.getLong("duration-minutes", 30) * 60),
                Math.max(1, section.getInt("base-goal", 100)),
                Math.max(0, section.getInt("goal-per-resident", 25)),
                clamp(section.getDouble("mitigation-cap", 0.75), 0, 0.95),
                mode,
                barColor,
                doubles(section.getConfigurationSection("buildings")),
                doubles(section.getConfigurationSection("ideologies")),
                List.copyOf(rules),
                List.copyOf(section.getStringList("success-commands")),
                List.copyOf(section.getStringList("failure-commands"))
        );
    }

    private Material specialMaterial(String key) {
        if (key.equalsIgnoreCase("FIRE_RESISTANCE")) return Material.POTION;
        return Material.matchMaterial(key);
    }

    private Map<String, Double> doubles(ConfigurationSection section) {
        if (section == null) return Map.of();
        Map<String, Double> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) result.put(key, Math.max(0, section.getDouble(key)));
        return Collections.unmodifiableMap(result);
    }

    private Material material(String name, Material fallback) {
        Material found = Material.matchMaterial(name);
        return found == null ? fallback : found;
    }

    private String prettify(String value) {
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public EventDefinition get(String id) {
        return id == null ? null : definitions.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<EventDefinition> all() {
        return List.copyOf(definitions.values());
    }
}
