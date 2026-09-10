package ru.neverland.townybuilds;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townybuilds.service.ProjectIcons;

public final class ProjectIconsSmoke {
    public static void main(String[] args) throws Exception {
        YamlConfiguration yaml;
        try (var in = ProjectIconsSmoke.class.getResourceAsStream("/projects.yml")) {
            yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        var icons = new HashSet<Material>();
        for (String id : List.of("agrarian_complex", "mill", "aqueduct", "irrigation_station", "water_tower",
                "water_wheel", "generator", "power_station", "fire_station", "city_moat", "watch_fortress", "guard", "temple", "cathedral")) {
            Material icon = Material.matchMaterial(yaml.getString("buildings." + id + ".icon"));
            check(icon != null && icons.add(icon), "Repeated or unknown icon: " + id);
        }
        check(ProjectIcons.resolve("mill", Material.WHEAT, false) == Material.WHEAT, "Unchanged icon moved");
        check(ProjectIcons.resolve("agrarian_complex", Material.WHEAT, false) == Material.DIAMOND_HOE, "Old config not migrated");
        check(ProjectIcons.resolve("aqueduct", Material.WATER_BUCKET, false) == Material.PRISMARINE_BRICKS, "Water icon not migrated");
        check(ProjectIcons.resolve("aqueduct", Material.DIAMOND, false) == Material.DIAMOND, "Admin choice overwritten");
        check(ProjectIcons.resolve("aqueduct", Material.WATER_BUCKET, true) == Material.WATER_BUCKET, "Custom project overwritten");
        System.out.println("ProjectIconsSmoke OK: distinct icons and saved-config migration");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
