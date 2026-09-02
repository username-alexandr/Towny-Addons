package ru.neverland.mintcamps.service;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcamps.model.StyleDefinition;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class StyleRegistry {
    private static final String[] ROLES = {"log", "stripped-log", "planks", "slab", "stairs", "fence",
            "wool", "carpet", "foundation", "stone-ring", "light", "banner", "bed"};
    private final JavaPlugin plugin;
    private final Map<String, StyleDefinition> styles = new LinkedHashMap<>();

    public StyleRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        styles.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "styles.yml"));
        ConfigurationSection root = yaml.getConfigurationSection("styles");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            Map<String, Material> materials = new LinkedHashMap<>();
            for (String role : ROLES) {
                Material material = Material.matchMaterial(yaml.getString("styles." + id + "." + role, "OAK_PLANKS"));
                if (material == null || material.isAir()) {
                    plugin.getLogger().warning("Некорректный материал стиля " + id + ": " + role);
                    material = Material.OAK_PLANKS;
                }
                materials.put(role, material);
            }
            styles.put(id.toLowerCase(Locale.ROOT), new StyleDefinition(id.toLowerCase(Locale.ROOT),
                    yaml.getString("styles." + id + ".name", id), Collections.unmodifiableMap(materials)));
        }
        plugin.getLogger().info("Загружено биомных стилей лагеря: " + styles.size() + ".");
    }

    public Optional<StyleDefinition> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(styles.get(id.toLowerCase(Locale.ROOT)));
    }

    public StyleDefinition detect(World world, Biome biome) {
        if (world.getEnvironment() == World.Environment.NETHER) return fallback("nether");
        String key = biome.getKey().getKey().toLowerCase(Locale.ROOT);
        if (containsAny(key, "desert", "badlands", "eroded_badlands", "wooded_badlands")) return fallback("desert");
        if (containsAny(key, "jungle", "bamboo")) return fallback("jungle");
        if (key.contains("savanna")) return fallback("savanna");
        if (containsAny(key, "snow", "ice", "frozen", "grove", "peak", "windswept_hills")) return fallback("snowy");
        if (containsAny(key, "taiga", "old_growth_pine", "old_growth_spruce")) return fallback("taiga");
        return fallback("plains");
    }

    private StyleDefinition fallback(String id) {
        StyleDefinition value = styles.get(id);
        if (value != null) return value;
        if (!styles.isEmpty()) return styles.values().iterator().next();
        return new StyleDefinition("plains", "Равнины", Map.ofEntries(
                Map.entry("log", Material.OAK_LOG), Map.entry("stripped-log", Material.STRIPPED_OAK_LOG),
                Map.entry("planks", Material.OAK_PLANKS), Map.entry("slab", Material.OAK_SLAB),
                Map.entry("stairs", Material.OAK_STAIRS), Map.entry("fence", Material.OAK_FENCE),
                Map.entry("wool", Material.GREEN_WOOL), Map.entry("carpet", Material.GREEN_CARPET),
                Map.entry("foundation", Material.STONE_BRICKS), Map.entry("stone-ring", Material.COBBLESTONE_SLAB),
                Map.entry("light", Material.LANTERN), Map.entry("banner", Material.GREEN_BANNER),
                Map.entry("bed", Material.GREEN_BED)));
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
