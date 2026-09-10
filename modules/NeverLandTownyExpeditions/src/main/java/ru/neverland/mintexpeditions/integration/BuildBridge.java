package ru.neverland.mintexpeditions.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/** Необязательный мост без жёсткой зависимости и без цикла загрузки Paper. */
public final class BuildBridge {
    private final JavaPlugin plugin;
    private boolean warned;

    public BuildBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public double expeditionTimeMultiplier(UUID townId) {
        if (projectLevel(townId, "celestial_orrery") <= 0) return 1.0;
        return Math.max(1.0, plugin.getConfig().getDouble("builds.celestial-orrery-time-multiplier", 1.25));
    }

    private int projectLevel(UUID townId, String projectId) {
        if (townId == null) return 0;
        String pluginName = plugin.getConfig().getString("builds.plugin", "NeverLandTownyBuilds");
        Plugin source = Bukkit.getPluginManager().getPlugin(pluginName);
        if (source == null || !source.isEnabled()) return 0;
        try {
            Field field = source.getClass().getDeclaredField("dataStore");
            field.setAccessible(true);
            Object store = field.get(source);
            Object townData = store.getClass().getMethod("town", UUID.class).invoke(store, townId);
            Method level = townData.getClass().getMethod("operationalLevel", String.class);
            return ((Number) level.invoke(townData, projectId)).intValue();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Бонус Небесного оррерия недоступен: " + exception.getMessage());
            }
            return 0;
        }
    }
}
