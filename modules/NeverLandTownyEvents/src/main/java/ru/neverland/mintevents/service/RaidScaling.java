package ru.neverland.mintevents.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.SafeYaml;
import ru.neverland.mintevents.model.RaidState;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Read once per new raid. Existing raids keep their persisted profile and wave plan. */
public final class RaidScaling {
    public record Profile(String source, int buildings, int levels, double score,
                          double countMultiplier, double healthMultiplier, double damageMultiplier) {
        public Profile {
            if (!Set.of("development", "disabled", "unavailable", "legacy").contains(source)
                    || buildings < 0 || levels < 0 || !Double.isFinite(score) || score < 0
                    || score > 1_000_000 || buildings > 100_000 || levels > 500_000)
                throw new IllegalArgumentException("Повреждены показатели развития набега");
            bounded(countMultiplier, 1, 5, "count");
            bounded(healthMultiplier, 1, 5, "health");
            bounded(damageMultiplier, 1, 3, "damage");
        }
        public static Profile legacy() { return new Profile("legacy", 0, 0, 0, 1, 1, 1); }
        public String label() {
            double strength = Math.max(countMultiplier, Math.max(healthMultiplier, damageMultiplier));
            return strength < 1.15 ? "Обычный" : strength < 1.5 ? "Усиленный"
                    : strength < 2 ? "Тяжёлый" : "Осадный";
        }
        public List<String> description() {
            var lines = new ArrayList<String>();
            lines.add("Сила набега: " + label());
            if (source.equals("development")) {
                lines.add("Построек: " + buildings + " · сумма уровней: " + levels);
                lines.add("Врагов: ×" + decimal(countMultiplier) + " · здоровье: ×" + decimal(healthMultiplier));
                lines.add("Урон: ×" + decimal(damageMultiplier));
                lines.add("Сила фиксируется в начале набега.");
            } else lines.add(switch (source) {
                case "legacy" -> "Набег начался до обновления: прежняя сложность.";
                case "disabled" -> "Усиление по развитию выключено.";
                default -> "Нет данных о зданиях: обычная сложность.";
            });
            lines.add("Защитные здания по-прежнему ослабляют врагов.");
            return List.copyOf(lines);
        }
        public void save(ConfigurationSection out) {
            out.set("source", source); out.set("buildings", buildings); out.set("levels", levels);
            out.set("score", score); out.set("count-multiplier", countMultiplier);
            out.set("health-multiplier", healthMultiplier); out.set("damage-multiplier", damageMultiplier);
        }
        public static Profile load(ConfigurationSection in) {
            return new Profile(SafeYaml.text(in, "source"), SafeYaml.intValue(in, "buildings"),
                    SafeYaml.intValue(in, "levels"), SafeYaml.doubleValue(in, "score"),
                    SafeYaml.doubleValue(in, "count-multiplier"), SafeYaml.doubleValue(in, "health-multiplier"),
                    SafeYaml.doubleValue(in, "damage-multiplier"));
        }
        private static String decimal(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    }

    private final boolean enabled;
    private final double fullStrengthScore, maxCount, maxHealth, maxDamage;
    private final int maxWaveSize;
    private final Map<String, Double> weights;
    private final Set<String> fixedCountMobs;

    public RaidScaling(JavaPlugin plugin) {
        this(configuration(plugin));
    }
    public RaidScaling(ConfigurationSection yaml) {
        enabled = SafeYaml.booleanValue(yaml, "enabled", true);
        fullStrengthScore = bounded(SafeYaml.doubleValue(yaml, "full-strength-score", 200), 1, 1_000_000, "full-strength-score");
        maxCount = bounded(SafeYaml.doubleValue(yaml, "maximum.count", 2.5), 1, 5, "maximum.count");
        maxHealth = bounded(SafeYaml.doubleValue(yaml, "maximum.health", 2), 1, 5, "maximum.health");
        maxDamage = bounded(SafeYaml.doubleValue(yaml, "maximum.damage", 1.5), 1, 3, "maximum.damage");
        maxWaveSize = SafeYaml.intValue(yaml, "maximum.wave-size", 60);
        if (maxWaveSize < 1 || maxWaveSize > 200) throw new IllegalArgumentException("maximum.wave-size: нужно 1..200");
        weights = new HashMap<>();
        var section = SafeYaml.section(yaml, "project-weights");
        if (section != null) for (String id : section.getKeys(false))
            weights.put(id, bounded(SafeYaml.doubleValue(section, id), 0, 100, "project-weights." + id));
        fixedCountMobs = yaml.contains("fixed-count-mobs") ? Set.copyOf(SafeYaml.strings(yaml, "fixed-count-mobs")) : Set.of("captain");
    }
    private static ConfigurationSection configuration(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "raid-scaling.yml");
        if (!file.exists()) plugin.saveResource("raid-scaling.yml", false);
        YamlConfiguration yaml = SafeYaml.load(file.toPath());
        try (var input = plugin.getResource("raid-scaling.yml")) {
            if (input != null) yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8)));
        } catch (IOException ex) { throw new IllegalStateException(ex); }
        return yaml;
    }
    public boolean enabled() { return enabled; }
    public Profile profile(Map<String, Integer> completed, boolean available) {
        int buildings = 0, levels = 0; double score = 0;
        for (var entry : completed.entrySet()) {
            int level = Math.max(0, Math.min(5, entry.getValue()));
            if (level == 0) continue;
            buildings++; levels += level;
            score = Math.min(1_000_000, score + level * weights.getOrDefault(entry.getKey(), 1.0));
        }
        String source = !enabled ? "disabled" : !available ? "unavailable" : "development";
        double factor = source.equals("development") ? Math.min(1, score / fullStrengthScore) : 0;
        return new Profile(source, buildings, levels, score, 1 + (maxCount - 1) * factor,
                1 + (maxHealth - 1) * factor, 1 + (maxDamage - 1) * factor);
    }
    public List<RaidCatalog.Wave> plan(RaidCatalog catalog, Profile profile) {
        var result = new ArrayList<RaidCatalog.Wave>();
        for (int n = 1; n <= RaidState.WAVES; n++) result.add(scale(catalog.wave(n), profile));
        return List.copyOf(result);
    }
    public RaidCatalog.Wave scale(RaidCatalog.Wave base, Profile profile) {
        // Preserve every configured enemy, including a single captain. Only add a bounded prefix.
        var eligible = new LinkedHashMap<String, Integer>();
        for (String id : base.mobs()) if (!fixedCountMobs.contains(id)) eligible.merge(id, 1, Integer::sum);
        var cycle = new ArrayList<String>();
        for (int i = 0; ; i++) {
            boolean added = false;
            for (var entry : eligible.entrySet()) if (entry.getValue() > i) { cycle.add(entry.getKey()); added = true; }
            if (!added) break;
        }
        int limit = Math.max(base.mobs().size(), maxWaveSize); // never shrink an administrator's existing composition
        int extra = Math.min(limit - base.mobs().size(), (int) Math.round(cycle.size() * (profile.countMultiplier() - 1)));
        var mobs = new ArrayList<>(base.mobs());
        for (int i = 0; i < extra && !cycle.isEmpty(); i++) mobs.add(cycle.get(i % cycle.size()));
        return new RaidCatalog.Wave(List.copyOf(mobs), base.healthMultiplier() * profile.healthMultiplier(),
                base.damageMultiplier() * profile.damageMultiplier());
    }
    private static double bounded(double value, double min, double max, String key) {
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Недопустимый параметр " + key);
        return value;
    }
}
