package ru.neverland.townyideologies.service;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townyideologies.model.IdeologyDefinition;
import ru.neverland.townyideologies.util.EffectFormatter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class IdeologyRegistry {
    private final JavaPlugin plugin;
    private final Map<String, IdeologyDefinition> definitions = new LinkedHashMap<>();
    private YamlConfiguration yaml;

    public IdeologyRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "ideologies.yml"));
        definitions.clear();
        ConfigurationSection root = yaml.getConfigurationSection("ideologies");
        if (root == null) {
            plugin.getLogger().severe("В ideologies.yml отсутствует раздел ideologies.");
            return;
        }
        for (String sourceId : root.getKeys(false)) {
            String id = sourceId.toLowerCase(Locale.ROOT);
            String base = "ideologies." + sourceId + ".";
            if (!yaml.getBoolean(base + "enabled", true)) continue;
            Material material = Material.matchMaterial(yaml.getString(base + "material", "PAPER"));
            if (material == null || material.isAir()) material = Material.PAPER;
            Map<Integer, Double> prices = new LinkedHashMap<>();
            for (int level = 2; level <= IdeologyDefinition.MAX_LEVEL; level++) {
                prices.put(level, yaml.getDouble(base + "upgrade-prices." + level, -1.0D));
            }
            IdeologyDefinition definition = new IdeologyDefinition(
                    id,
                    yaml.getString(base + "name", sourceId),
                    material,
                    yaml.getString(base + "itemsadder-icon", ""),
                    Math.max(0, Math.min(26, yaml.getInt(base + "slot", 10))),
                    Math.max(0.0D, yaml.getDouble(base + "selection-price", 200.0D)),
                    Collections.unmodifiableMap(prices),
                    List.copyOf(yaml.getStringList(base + "description")),
                    List.copyOf(yaml.getStringList(base + "bonus-description"))
            );
            definitions.put(id, definition);
        }
        plugin.getLogger().info("Загружено идеологий: " + definitions.size() + ".");
    }

    public Collection<IdeologyDefinition> all() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public Optional<IdeologyDefinition> find(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(definitions.get(id.toLowerCase(Locale.ROOT)));
    }

    public double levelDouble(String id, String path, int level, double fallback) {
        List<?> values = yaml.getList("ideologies." + id + ".bonuses." + path, Collections.emptyList());
        int index = Math.max(0, Math.min(values.size() - 1, level - 1));
        if (values.isEmpty()) return fallback;
        Object value = values.get(index);
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public int levelInt(String id, String path, int level, int fallback) {
        return (int) Math.round(levelDouble(id, path, level, fallback));
    }

    public List<String> levelStrings(String id, String path, int level) {
        String full = "ideologies." + id + ".bonuses." + path + ".level-" + Math.max(1, Math.min(5, level));
        return new ArrayList<>(yaml.getStringList(full));
    }

    public Map<String, String> bonusPlaceholders(IdeologyDefinition definition, int level) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("growth_multiplier", decimal(levelDouble(definition.id(), "growth-multiplier", level, 1.0D)));
        values.put("smelt_multiplier", decimal(levelDouble(definition.id(), "smelt-multiplier", level, 1.0D)));
        values.put("health_hearts", decimal(levelDouble(definition.id(), "health-hearts", level, 0.0D)));
        values.put("minecart_multiplier", decimal(levelDouble(definition.id(), "minecart-multiplier", level, 1.0D)));
        values.put("placement_cooldown", Integer.toString(levelInt(definition.id(), "placement-cooldown-ticks", level, 0)));
        values.put("trade_multiplier", decimal(levelDouble(definition.id(), "trade-output-multiplier", level, 1.0D)));
        List<String> effects = levelStrings(definition.id(), "effects", level);
        values.put("defense_effects", effects.isEmpty() ? "нет" : effects.stream()
                .map(effect -> EffectFormatter.format(effect,
                        key -> yaml.getString("effect-names." + key, "")))
                .collect(java.util.stream.Collectors.joining(", ")));
        return values;
    }

    private String decimal(double value) {
        if (Math.rint(value) == value) return Long.toString(Math.round(value));
        return String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
