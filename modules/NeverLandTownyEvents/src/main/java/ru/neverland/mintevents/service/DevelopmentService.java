package ru.neverland.mintevents.service;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintevents.api.TownDevelopmentProvider;
import ru.neverland.mintevents.model.EventDefinition;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DevelopmentService {
    private final JavaPlugin plugin;
    private final Map<String, CachedYaml> cache = new HashMap<>();

    public DevelopmentService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public double protection(UUID townId, EventDefinition definition) {
        double result = 0;
        for (Map.Entry<String, Double> entry : definition.buildingModifiers().entrySet()) {
            result += buildingLevel(townId, entry.getKey()) * entry.getValue();
        }
        for (Map.Entry<String, Double> entry : definition.ideologyModifiers().entrySet()) {
            result += ideologyLevel(townId, entry.getKey()) * entry.getValue();
        }
        return Math.min(definition.mitigationCap(), Math.max(0, result));
    }

    public Map<String, Integer> buildingLevels(UUID townId, EventDefinition definition) {
        Map<String, Integer> result = new HashMap<>();
        for (String id : definition.buildingModifiers().keySet()) result.put(id, buildingLevel(townId, id));
        return result;
    }

    public Map<String, Integer> ideologyLevels(UUID townId, EventDefinition definition) {
        Map<String, Integer> result = new HashMap<>();
        for (String id : definition.ideologyModifiers().keySet()) result.put(id, ideologyLevel(townId, id));
        return result;
    }

    private int buildingLevel(UUID townId, String id) {
        if(!ru.neverland.integration.BuildingOperations.active(townId,id))return 0;
        TownDevelopmentProvider provider = provider();
        if (provider != null) return safe(() -> provider.buildingLevel(townId, id));
        String path = plugin.getConfig().getString("development-sources.builds.path",
                "towns.%town_uuid%.levels.%id%");
        return fileLevel("development-sources.builds", path, townId, id);
    }

    private int ideologyLevel(UUID townId, String id) {
        TownDevelopmentProvider provider = provider();
        if (provider != null) return safe(() -> provider.ideologyLevel(townId, id));
        List<String> paths = plugin.getConfig().getStringList("development-sources.ideologies.paths");
        for (String path : paths) {
            int level = fileLevel("development-sources.ideologies", path, townId, id);
            if (level > 0) return level;
        }
        return 0;
    }

    private TownDevelopmentProvider provider() {
        RegisteredServiceProvider<TownDevelopmentProvider> registration =
                Bukkit.getServicesManager().getRegistration(TownDevelopmentProvider.class);
        return registration == null ? null : registration.getProvider();
    }

    private int safe(LevelCall call) {
        try { return Math.max(0, call.level()); }
        catch (RuntimeException exception) { return 0; }
    }

    private int fileLevel(String root, String template, UUID townId, String id) {
        String pluginName = plugin.getConfig().getString(root + ".plugin", "");
        String fileName = plugin.getConfig().getString(root + ".file", "town-data.yml");
        Plugin sourcePlugin = resolvePlugin(root, pluginName);
        if (sourcePlugin == null || !sourcePlugin.isEnabled()) return 0;
        File file = new File(sourcePlugin.getDataFolder(), fileName);
        if (!file.isFile()) return 0;
        long ttl = Math.max(1, plugin.getConfig().getLong("development-sources.cache-seconds", 30)) * 1000;
        CachedYaml cached = cache.get(file.getAbsolutePath());
        long now = System.currentTimeMillis();
        if (cached == null || cached.modified != file.lastModified() || now - cached.loadedAt >= ttl) {
            cached = new CachedYaml(YamlConfiguration.loadConfiguration(file), file.lastModified(), now);
            cache.put(file.getAbsolutePath(), cached);
        }
        String path = template.replace("%town_uuid%", townId.toString()).replace("%id%", id);
        return Math.max(0, cached.yaml.getInt(path, 0));
    }

    private Plugin resolvePlugin(String root, String configured) {
        List<String> candidates = root.endsWith("builds")
                ? List.of(configured, "NeverLandTownyBuilds")
                : List.of(configured, "NeverLandTownyIdeologies", "MintTownyIdeologies");
        for (String name : candidates) {
            if (name == null || name.isBlank()) continue;
            Plugin candidate = Bukkit.getPluginManager().getPlugin(name);
            if (candidate != null && candidate.isEnabled()) return candidate;
        }
        return null;
    }

    public void clearCache() { cache.clear(); }

    private interface LevelCall { int level(); }
    private record CachedYaml(YamlConfiguration yaml, long modified, long loadedAt) {}
}
