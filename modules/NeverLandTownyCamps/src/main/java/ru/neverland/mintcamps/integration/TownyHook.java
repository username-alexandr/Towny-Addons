package ru.neverland.mintcamps.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

public final class TownyHook {
    public enum PlacementResult { ALLOWED, CLAIMED, FOREIGN_TOWN, ERROR }

    private final JavaPlugin plugin;
    private boolean available;
    private Object api;
    private Method getTownAt;
    private Method getResident;
    private Method residentTown;
    private Method townUuid;

    public TownyHook(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        available = false;
        if (Bukkit.getPluginManager().getPlugin("Towny") == null) return;
        try {
            Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Class<?> residentClass = Class.forName("com.palmergames.bukkit.towny.object.Resident");
            Class<?> townClass = Class.forName("com.palmergames.bukkit.towny.object.Town");
            api = apiClass.getMethod("getInstance").invoke(null);
            getTownAt = apiClass.getMethod("getTown", Location.class);
            getResident = apiClass.getMethod("getResident", Player.class);
            residentTown = residentClass.getMethod("getTownOrNull");
            townUuid = townClass.getMethod("getUUID");
            available = true;
            plugin.getLogger().info("Towny обнаружен: контроль диких земель включён.");
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Не удалось подключить API Towny: " + exception.getMessage());
        }
    }

    public PlacementResult canPlace(Player player, Location location) {
        if (!available || player.hasPermission("mintcamps.bypass.placement")) return PlacementResult.ALLOWED;
        try {
            Object locationTown = getTownAt.invoke(api, location);
            if (locationTown == null) return PlacementResult.ALLOWED;
            boolean wildernessOnly = plugin.getConfig().getBoolean("settings.placement.towny.wilderness-only", true);
            boolean ownAllowed = plugin.getConfig().getBoolean("settings.placement.towny.allow-in-own-town", false);
            if (!wildernessOnly && !ownAllowed) return PlacementResult.ALLOWED;
            if (!ownAllowed) return PlacementResult.CLAIMED;
            Object resident = getResident.invoke(api, player);
            Object playerTown = resident == null ? null : residentTown.invoke(resident);
            if (playerTown == null) return PlacementResult.FOREIGN_TOWN;
            UUID locationId = (UUID) townUuid.invoke(locationTown);
            UUID playerId = (UUID) townUuid.invoke(playerTown);
            return locationId.equals(playerId) ? PlacementResult.ALLOWED : PlacementResult.FOREIGN_TOWN;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Ошибка проверки территории Towny: " + exception.getMessage());
            return PlacementResult.ERROR;
        }
    }
}
