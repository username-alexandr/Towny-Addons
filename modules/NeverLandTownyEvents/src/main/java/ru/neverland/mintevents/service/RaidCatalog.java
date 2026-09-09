package ru.neverland.mintevents.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Separate configuration ensures existing servers receive the ten-wave defaults. */
public final class RaidCatalog {
    public record MobSpec(String id, EntityType type, String name, int points, double health,
                          double damage, String provider, String model, Map<String, String> equipment) {}
    public record Wave(List<String> mobs, double healthMultiplier, double damageMultiplier) {}
    private final Map<String, MobSpec> mobs = new LinkedHashMap<>();
    private final List<Wave> waves = new ArrayList<>();
    public RaidCatalog(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "raids.yml");
        if (!file.exists()) plugin.saveResource("raids.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        try (InputStream input = plugin.getResource("raids.yml")) {
            if (input != null) yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8)));
        } catch (IOException ex) { throw new IllegalStateException(ex); }
        load(yaml);
    }
    public RaidCatalog(ConfigurationSection yaml) { load(yaml); }
    private void load(ConfigurationSection yaml) {
        ConfigurationSection root = Objects.requireNonNull(yaml.getConfigurationSection("mobs"), "mobs");
        for (String id : root.getKeys(false)) {
            ConfigurationSection mob = root.getConfigurationSection(id);
            EntityType type = EntityType.valueOf(mob.getString("type", "ZOMBIE").toUpperCase(Locale.ROOT));
            if (!type.isAlive() || !type.isSpawnable()) throw new IllegalArgumentException("Неверный тип моба: " + id);
            String provider = mob.getString("provider", "vanilla").toLowerCase(Locale.ROOT);
            if (!Set.of("vanilla", "modelengine", "itemsadder").contains(provider)) throw new IllegalArgumentException("Неверный provider: " + id);
            Map<String, String> gear = new LinkedHashMap<>();
            ConfigurationSection items = mob.getConfigurationSection("equipment");
            if (items != null) for (String slot : items.getKeys(false)) {
                if (!Set.of("hand", "offhand", "helmet", "chestplate", "leggings", "boots").contains(slot)) throw new IllegalArgumentException("Неверный слот: " + slot);
                gear.put(slot, items.getString(slot));
            }
            mobs.put(id, new MobSpec(id, type, mob.getString("name", "Разбойник"),
                    Math.max(0, mob.getInt("points", 1)), positive(mob.getDouble("health", 20)),
                    positive(mob.getDouble("damage", 3)), provider, mob.getString("model", ""), Map.copyOf(gear)));
        }
        for (int i = 1; i <= 10; i++) {
            ConfigurationSection wave = Objects.requireNonNull(yaml.getConfigurationSection("waves." + i), "wave " + i);
            ConfigurationSection counts = Objects.requireNonNull(wave.getConfigurationSection("mobs"), "wave mobs " + i);
            List<String> composition = new ArrayList<>();
            for (String id : counts.getKeys(false)) {
                if (!mobs.containsKey(id)) throw new IllegalArgumentException("Неизвестный моб: " + id);
                int count = counts.getInt(id);
                if (count < 0 || count > 200) throw new IllegalArgumentException("Число мобов 0..200: " + id);
                for (int n = 0; n < count; n++) composition.add(id);
            }
            if (composition.isEmpty() || composition.size() > 200) throw new IllegalArgumentException("Волна должна содержать 1..200 мобов: " + i);
            waves.add(new Wave(List.copyOf(composition), positive(wave.getDouble("health-multiplier", 1)), positive(wave.getDouble("damage-multiplier", 1))));
        }
    }
    private static double positive(double number) {
        if (!Double.isFinite(number) || number <= 0 || number > 10000) throw new IllegalArgumentException("Недопустимая характеристика: " + number);
        return number;
    }
    public MobSpec mob(String id) { return mobs.get(id); }
    public Wave wave(int number) { return waves.get(Math.max(1, Math.min(10, number)) - 1); }
}
