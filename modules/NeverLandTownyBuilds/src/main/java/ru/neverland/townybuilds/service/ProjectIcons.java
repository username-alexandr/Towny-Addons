package ru.neverland.townybuilds.service;

import java.util.Map;
import org.bukkit.Material;

/** Upgrade only former bundled icons; administrative choices remain intact. */
public final class ProjectIcons {
    private record Change(Material previous, Material current) {}
    private static final Map<String, Change> CHANGES = Map.ofEntries(
            Map.entry("agrarian_complex", new Change(Material.WHEAT, Material.DIAMOND_HOE)),
            Map.entry("aqueduct", new Change(Material.WATER_BUCKET, Material.PRISMARINE_BRICKS)),
            Map.entry("irrigation_station", new Change(Material.WATER_BUCKET, Material.COPPER_HOE)),
            Map.entry("water_tower", new Change(Material.WATER_BUCKET, Material.BUCKET)),
            Map.entry("fire_station", new Change(Material.WATER_BUCKET, Material.FIRE_CHARGE)),
            Map.entry("city_moat", new Change(Material.WATER_BUCKET, Material.LILY_PAD)),
            Map.entry("watch_fortress", new Change(Material.SHIELD, Material.POLISHED_BLACKSTONE_BRICKS)),
            Map.entry("guard", new Change(Material.SHIELD, Material.IRON_HELMET)),
            Map.entry("cathedral", new Change(Material.TOTEM_OF_UNDYING, Material.END_CRYSTAL)));

    private ProjectIcons() {}

    public static Material resolve(String projectId, Material configured, boolean custom) {
        Change change = CHANGES.get(projectId);
        return !custom && change != null && configured == change.previous() ? change.current() : configured;
    }
}
