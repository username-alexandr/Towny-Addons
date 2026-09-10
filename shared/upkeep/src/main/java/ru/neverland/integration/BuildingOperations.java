package ru.neverland.integration;

import org.bukkit.Bukkit;
import java.util.UUID;

/** Resolve at use time: Upkeep depends on Builds, so Builds must not depend back on Upkeep. */
public final class BuildingOperations {
    private BuildingOperations() {}
    public static boolean active(UUID town, String project) {
        var plugin = Bukkit.getPluginManager().getPlugin("NeverLandTownyUpkeep");
        if (plugin == null) return true;
        if (!plugin.isEnabled()) return false;
        try {
            Class<?> api = Class.forName("ru.neverland.townyupkeep.api.TownyUpkeepApi", true, plugin.getClass().getClassLoader());
            Object service = Bukkit.getServicesManager().load(api);
            return service != null && Boolean.TRUE.equals(api.getMethod("active", UUID.class, String.class).invoke(service, town, project));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) { return false; }
    }
    public static int level(UUID town, String project, int builtLevel) {
        return builtLevel > 0 && active(town, project) ? builtLevel : 0;
    }
}
