package ru.neverland.townyenvironment.integration;

import java.util.*;
import org.bukkit.*;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Coord;
import ru.neverland.core.ApiServices;
import ru.neverland.integration.BuildingOperations;
import ru.neverland.townyenvironment.config.EnvironmentSettings;

/** Ecological pressure of operational structures, not a resource-production accounting ledger. */
public class EnvironmentMetrics {
    public Map<String, Integer> active(UUID town, EnvironmentSettings settings) throws ReflectiveOperationException {
        var builds = ApiServices.require("NeverLandTownyBuilds", "ru.neverland.townybuilds.api.TownyBuildsApi", "buildingFootprints");
        // Distinguish missing optional addons from an installed provider outage before reading building flags.
        requireOptional(ApiServices.connect("NeverLandTownyUpkeep", "ru.neverland.townyupkeep.api.TownyUpkeepApi", 1, "active"));
        requireOptional(ApiServices.connect("NeverLandTownyPower", "ru.neverland.townypower.api.TownyPowerApi", 1, "powered"));
        requireOptional(ApiServices.connect("NeverLandTownySpecialization", "ru.neverland.townyspecialization.api.TownySpecializationApi", 1, "canUseBuilding"));
        Set<?> stopped = Set.of();
        var resources = ApiServices.connect("NeverLandTownyResources", "ru.neverland.townyresources.api.TownyResourcesApi", 1, "resources");
        if (resources.state() != ApiServices.State.NOT_INSTALLED) {
            Object snapshot = ((Optional<?>)resources.invoke("resources", new Class<?>[]{UUID.class}, town)).orElseThrow(() -> new IllegalStateException("Ожидаются данные ресурсов"));
            Object state = read(snapshot, "state"); stopped = (Set<?>)read(state, "paused");
            // Read only explicit building pauses. Resource forecasts may themselves wait for ecology.
        }
        Object raw = builds.invoke("buildingFootprints", new Class<?>[]{UUID.class}, town);
        if (!(raw instanceof Map<?, ?> footprints)) throw new IllegalStateException("Некорректный каталог зданий");
        Map<String, Integer> active = new TreeMap<>();
        for (var entry : footprints.entrySet()) {
            String id = entry.getKey().toString(); if (!settings.buildings().containsKey(id)) continue;
            Object footprint = entry.getValue(); int level = integer(footprint, "completedLevel");
            if (level < 0 || level > 5) throw new IllegalStateException("Неверный уровень здания " + id);
            if (level > 0 && !stopped.contains(id) && owned(town, footprint) && BuildingOperations.active(town, id)) active.put(id, level);
        }
        return Map.copyOf(active);
    }
    private void requireOptional(ApiServices.Connection c) {
        if (c.state() != ApiServices.State.NOT_INSTALLED && !c.ready()) throw new IllegalStateException("Ожидаются данные действующих зданий");
    }
    private static Object read(Object value, String method) throws ReflectiveOperationException { return value.getClass().getMethod(method).invoke(value); }
    private static int integer(Object value, String method) throws ReflectiveOperationException { return ((Number)read(value, method)).intValue(); }
    private boolean owned(UUID town, Object footprint) throws ReflectiveOperationException {
        World world = Bukkit.getWorld((UUID)read(footprint, "worldId"));
        if (world == null) throw new IllegalStateException("Мир здания недоступен");
        int size = Coord.getCellSize(); if (size < 1) throw new IllegalStateException("Неверный размер участка");
        int minX = Math.floorDiv(integer(footprint, "minX"), size), maxX = Math.floorDiv(integer(footprint, "maxX"), size);
        int minZ = Math.floorDiv(integer(footprint, "minZ"), size), maxZ = Math.floorDiv(integer(footprint, "maxZ"), size);
        long width = (long)maxX - minX + 1, depth = (long)maxZ - minZ + 1;
        if (width < 1 || depth < 1 || width > 4096 || depth > 4096 || width * depth > 4096) throw new IllegalStateException("Неверная площадка здания");
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            var owner = TownyAPI.getInstance().getTown(new Location(world, (double)x * size, world.getMinHeight(), (double)z * size));
            if (owner == null || !town.equals(owner.getUUID())) return false;
        }
        return true;
    }
}
