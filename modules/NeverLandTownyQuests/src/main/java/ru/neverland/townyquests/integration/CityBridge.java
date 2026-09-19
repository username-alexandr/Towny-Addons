package ru.neverland.townyquests.integration;

import com.palmergames.bukkit.towny.object.*;
import org.bukkit.Bukkit;
import java.util.*;
import ru.neverland.core.ApiServices;
import ru.neverland.townyquests.model.*;
import ru.neverland.townyquests.model.ProjectEngine.Observation;
import ru.neverland.townyquests.model.WaterCoverage.Cell;

/** Reads public provider contracts only. Unavailable observations never erase progress. */
public final class CityBridge {
    private static Object read(Object value, String method) throws Exception { return value.getClass().getMethod(method).invoke(value); }
    private static int number(Object value, String method) throws Exception { return ((Number) read(value, method)).intValue(); }
    private static int level(UUID town, String building) throws Exception {
        return ((Number) ApiServices.call("NeverLandTownyBuilds", "ru.neverland.townybuilds.api.TownyBuildsApi",
                "operationalLevel", new Class<?>[]{UUID.class, String.class}, town, building)).intValue();
    }
    private static Map<?, ?> footprints(UUID town) throws Exception {
        return (Map<?, ?>) ApiServices.call("NeverLandTownyBuilds", "ru.neverland.townybuilds.api.TownyBuildsApi",
                "buildingFootprints", new Class<?>[]{UUID.class}, town);
    }
    public static Set<Cell> owned(Town town) {
        Set<Cell> result = new HashSet<>();
        for (var block : town.getTownBlocks()) {
            var coord = block.getWorldCoord(); var world = Bukkit.getWorld(coord.getWorldName());
            if (world != null) result.add(new Cell(world.getUID(), coord.getX(), coord.getZ()));
        }
        return Set.copyOf(result);
    }
    private static Set<Cell> footprint(Object b) throws Exception {
        if (b == null || number(b, "completedLevel") < 1) return Set.of();
        UUID world = (UUID) read(b, "worldId"); int size = Coord.getCellSize();
        if (size < 1 || Bukkit.getWorld(world) == null) return Set.of();
        int x0 = Math.floorDiv(number(b, "minX"), size), x1 = Math.floorDiv(number(b, "maxX"), size);
        int z0 = Math.floorDiv(number(b, "minZ"), size), z1 = Math.floorDiv(number(b, "maxZ"), size);
        long width = (long) x1 - x0 + 1, depth = (long) z1 - z0 + 1;
        if (width < 1 || depth < 1 || width > 4096 || depth > 4096 || width * depth > 4096)
            throw new IllegalStateException("Некорректная территория здания");
        Set<Cell> result = new HashSet<>();
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) result.add(new Cell(world, x, z));
        return Set.copyOf(result);
    }
    public Observation observe(Town town, Project.Stage stage, int maxAge, long now) {
        try {
            UUID id = town.getUUID(); var land = owned(town); var buildings = footprints(id);
            if (stage.kind() == Project.Kind.BUILDING) {
                var cells = footprint(buildings.get(stage.building()));
                int value = !cells.isEmpty() && land.containsAll(cells) ? level(id, stage.building()) : 0;
                return new Observation(true, value, "Действующее здание на территории города: " + value + "/" + stage.target());
            }
            // Verify Resources before evaluating shortages: an outage must freeze, not reset, the hold.
            var snapshot = (Optional<?>) ApiServices.call("NeverLandTownyResources", "ru.neverland.townyresources.api.TownyResourcesApi",
                    "resources", new Class<?>[]{UUID.class}, id);
            if (snapshot.isEmpty()) return Observation.waiting("Ожидается расчёт ресурсов города");
            var view = snapshot.get(); var state = read(view, "state");
            long last = ((Number) read(state, "lastCycle")).longValue();
            if (Boolean.TRUE.equals(read(view, "paused")) || !Boolean.TRUE.equals(read(view, "populationLinked"))
                    || last <= 0 || last > now || now - last > maxAge * 1000L)
                return Observation.waiting("Ожидается свежий расчёт воды и населения");
            var raw = (Collection<?>) ApiServices.call("NeverLandTownyDistricts", "ru.neverland.townydistricts.api.TownyDistrictsApi",
                    "districts", new Class<?>[]{UUID.class}, id);
            Map<String, Set<Cell>> districts = new LinkedHashMap<>();
            for (var district : raw) {
                if (!id.equals(read(district, "town"))) throw new IllegalStateException("Район другого города");
                Set<Cell> cells = new HashSet<>();
                for (var cell : (Set<?>) read(district, "cells")) cells.add(new Cell((UUID) read(cell, "world"), number(cell, "x"), number(cell, "z")));
                if (districts.put((String) read(district, "id"), Set.copyOf(cells)) != null) throw new IllegalStateException("Повтор района");
            }
            boolean network = Boolean.TRUE.equals(ApiServices.call("NeverLandTownyBuilds", "ru.neverland.townybuilds.api.TownyBuildsApi",
                    "waterNetworkActive", new Class<?>[]{UUID.class}, id));
            double coverage = ((Number) read(view, "waterCoverage")).doubleValue();
            if (!Double.isFinite(coverage)) return Observation.waiting("Некорректный расчёт воды");
            if (!network || level(id, "aqueduct") < 1 || coverage < 1 || number(view, "population") < 1)
                return new Observation(true, 0, "Нужны акведук, водонапорная башня, резервуар, насосная и 100% воды для населения");
            for (String building : List.of("water_tower", "reservoir", "pumping_station")) {
                var cells = footprint(buildings.get(building));
                if (cells.isEmpty() || !land.containsAll(cells)) return new Observation(true, 0, "Водная сеть должна находиться на территории города");
            }
            var supplied = WaterCoverage.supplied(land, footprint(buildings.get("aqueduct")), districts);
            return new Observation(true, supplied.size(), "Районы с водой: " + supplied.size() + "/" + stage.target()
                    + (supplied.isEmpty() ? "" : " — " + String.join(", ", new TreeSet<>(supplied))));
        } catch (Exception | LinkageError ex) {
            return Observation.waiting("Ожидается доступ к зданиям, районам или ресурсам: " + ex.getClass().getSimpleName());
        }
    }
}
