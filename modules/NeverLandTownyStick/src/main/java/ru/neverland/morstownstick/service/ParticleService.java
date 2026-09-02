package ru.neverland.morstownstick.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.morstownstick.integration.TownyFacade;
import ru.neverland.morstownstick.model.BorderEdge;

import java.util.List;

public final class ParticleService {
    private final JavaPlugin plugin;
    private final SelectionService selections;
    private final TownyFacade towny;
    private final BorderCache borderCache;
    private BukkitTask task;
    private long elapsedTicks;

    public ParticleService(JavaPlugin plugin, SelectionService selections, TownyFacade towny, BorderCache borderCache) {
        this.plugin = plugin;
        this.selections = selections;
        this.towny = towny;
        this.borderCache = borderCache;
    }

    public void start() {
        stop();
        elapsedTicks = 0L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::render, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    private void render() {
        elapsedTicks++;
        boolean selectedTick = due("particles.selection");
        boolean borderTick = due("particles.town-border");
        if (!selectedTick && !borderTick) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (selectedTick && plugin.getConfig().getBoolean("particles.selection.enabled", true))
                renderSelection(player);
            if (borderTick && plugin.getConfig().getBoolean("particles.town-border.enabled", true))
                renderTownBorder(player);
        }
    }

    private boolean due(String path) {
        int interval = Math.max(1, plugin.getConfig().getInt(path + ".update-interval", 10));
        return elapsedTicks % interval == 0;
    }

    private void renderSelection(Player player) {
        List<BorderEdge> edges = BorderGeometry.individualEdges(selections.snapshot(player.getUniqueId()), towny.cellSize());
        renderEdges(player, edges, "particles.selection");
    }

    private void renderTownBorder(Player player) {
        Town ownTown = towny.town(player);
        if (ownTown != null) renderEdges(player, borderCache.edges(ownTown, elapsedTicks), "particles.town-border");
        if (plugin.getConfig().getBoolean("particles.town-border.own-town-only", true)) return;
        for (Town other : towny.towns()) {
            if (ownTown == null || !other.getUUID().equals(ownTown.getUUID()))
                renderEdges(player, borderCache.edges(other, elapsedTicks), "particles.town-border");
        }
    }

    private void renderEdges(Player player, List<BorderEdge> edges, String path) {
        Location location = player.getLocation();
        double renderDistance = Math.max(1.0, plugin.getConfig().getDouble(path + ".render-distance", 64.0));
        double maxDistanceSquared = renderDistance * renderDistance;
        double spacing = Math.max(0.5, plugin.getConfig().getDouble(path + ".spacing", 4.0));
        float size = (float) Math.max(0.1, plugin.getConfig().getDouble(path + ".size", 1.0));
        Particle.DustOptions dust = new Particle.DustOptions(parseColor(plugin.getConfig().getString(path + ".color")), size);
        double y = location.getY() + 0.2;
        for (BorderEdge edge : edges) {
            if (!edge.world().equals(location.getWorld().getName()) || edge.distanceSquared(location.getX(), location.getZ()) > maxDistanceSquared)
                continue;
            double length = Math.max(Math.abs(edge.x2() - edge.x1()), Math.abs(edge.z2() - edge.z1()));
            int points = Math.max(1, (int) Math.ceil(length / spacing));
            for (int index = 0; index <= points; index++) {
                double ratio = (double) index / points;
                double x = edge.x1() + (edge.x2() - edge.x1()) * ratio;
                double z = edge.z1() + (edge.z2() - edge.z1()) * ratio;
                player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
            }
        }
    }

    private Color parseColor(String value) {
        String normalized = value == null ? "55FF55" : value.trim().replace("#", "");
        try {
            return Color.fromRGB(Integer.parseInt(normalized, 16));
        } catch (IllegalArgumentException ignored) {
            return Color.fromRGB(0x55FF55);
        }
    }
}
