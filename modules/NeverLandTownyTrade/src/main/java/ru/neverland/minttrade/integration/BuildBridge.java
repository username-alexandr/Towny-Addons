package ru.neverland.minttrade.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public final class BuildBridge {
    private final JavaPlugin plugin;
    private boolean warned;
    public BuildBridge(JavaPlugin plugin) { this.plugin = plugin; }
    public int marketLevel(UUID townId) {
        return projectLevel(townId, plugin.getConfig().getString("market.project-id", "market"));
    }
    public int projectLevel(UUID townId, String projectId) {
        if (townId == null || projectId == null || projectId.isBlank()) return 0;
        String pluginName = plugin.getConfig().getString("warehouse.plugin", "NeverLandTownyBuilds");
        Plugin source = Bukkit.getPluginManager().getPlugin(pluginName);
        if (source == null || !source.isEnabled()) return 0;
        try {
            Field field = source.getClass().getDeclaredField("dataStore"); field.setAccessible(true);
            Object store = field.get(source);
            Object townData = store.getClass().getMethod("town", UUID.class).invoke(store, townId);
            Method level = townData.getClass().getMethod("level", String.class);
            return ((Number) level.invoke(townData, projectId)).intValue();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!warned) { warned = true; plugin.getLogger().severe("Не удалось прочитать уровни построек: " + exception.getMessage()); }
            return 0;
        }
    }
}
