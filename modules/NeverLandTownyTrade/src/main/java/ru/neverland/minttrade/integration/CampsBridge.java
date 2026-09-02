package ru.neverland.minttrade.integration;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.minttrade.model.RoutePoint;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CampsBridge {
    public record CampStop(UUID ownerTownId, RoutePoint point) {}
    private final JavaPlugin plugin;
    private final TownyHook towny;
    private boolean warned;
    public CampsBridge(JavaPlugin plugin, TownyHook towny) { this.plugin = plugin; this.towny = towny; }
    public List<CampStop> activeStops() {
        Plugin source = resolveCampPlugin();
        if (source == null) return List.of();
        try {
            File file = new File(source.getDataFolder(), "camps.yml");
            if (!file.exists()) return List.of();
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection root = yaml.getConfigurationSection("camps");
            if (root == null) return List.of();
            List<CampStop> stops = new ArrayList<>();
            for (String rawOwner : root.getKeys(false)) {
                String path = "camps." + rawOwner + ".";
                UUID owner = UUID.fromString(rawOwner);
                long burnUntil = yaml.getLong(path + "burn-until");
                if (plugin.getConfig().getBoolean("camps.require-active-fire", true) && burnUntil <= System.currentTimeMillis()) continue;
                Resident resident = towny.resident(owner); Town ownerTown = resident == null ? null : resident.getTownOrNull();
                if (ownerTown == null) continue;
                UUID worldId = UUID.fromString(yaml.getString(path + "world-id"));
                String worldName = yaml.getString(path + "world-name", "world");
                String[] anchor = yaml.getString(path + "anchor", "0,64,0").split(",", 3);
                if (anchor.length != 3) continue;
                int level = yaml.getInt(path + "level", 1);
                RoutePoint point = new RoutePoint(worldId, worldName, Double.parseDouble(anchor[0]) + 0.5,
                        Double.parseDouble(anchor[1]) + 1, Double.parseDouble(anchor[2]) + 0.5,
                        RoutePoint.Kind.CAMP, "Лагерь " + ownerTown.getName(), level, owner);
                stops.add(new CampStop(ownerTown.getUUID(), point));
            }
            return stops;
        } catch (RuntimeException exception) {
            if (!warned) { warned = true; plugin.getLogger().warning("Лагеря недоступны как перевалочные пункты: " + exception.getMessage()); }
            return List.of();
        }
    }

    private Plugin resolveCampPlugin() {
        for (String name : List.of(plugin.getConfig().getString("camps.plugin", "NeverLandTownyCamps"),
                "NeverLandTownyCamps", "MintTownyCamps")) {
            if (name == null || name.isBlank()) continue;
            Plugin found = Bukkit.getPluginManager().getPlugin(name);
            if (found != null && found.isEnabled()) return found;
        }
        return null;
    }
}
