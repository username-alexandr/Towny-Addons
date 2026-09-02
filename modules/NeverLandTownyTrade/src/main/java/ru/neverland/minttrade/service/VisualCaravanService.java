package ru.neverland.minttrade.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TraderLlama;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.minttrade.integration.TownyHook;
import ru.neverland.minttrade.model.Caravan;
import ru.neverland.minttrade.model.RoutePoint;
import ru.neverland.minttrade.util.ColorUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VisualCaravanService {
    private final JavaPlugin plugin;
    private final TradeRepository repository;
    private final TownyHook towny;
    private final Map<UUID, Visual> visuals = new HashMap<>();
    private BukkitTask task;
    public VisualCaravanService(JavaPlugin plugin, TradeRepository repository, TownyHook towny) {
        this.plugin = plugin; this.repository = repository; this.towny = towny;
    }
    public void start() {
        stop(); if (!plugin.getConfig().getBoolean("visuals.enabled", true)) return;
        long period = Math.max(2, plugin.getConfig().getLong("visuals.update-ticks", 10));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }
    public void stop() { if (task != null) task.cancel(); task = null; visuals.values().forEach(this::remove); visuals.clear(); }
    private void tick() {
        for (UUID id : new ArrayList<>(visuals.keySet())) if (repository.caravans().stream().noneMatch(value -> value.id().equals(id))) removeVisual(id);
        for (Caravan caravan : repository.caravans()) update(caravan);
    }
    private void update(Caravan caravan) {
        Location location = position(caravan); if (location == null || location.getWorld() == null) { removeVisual(caravan.id()); return; }
        double range = plugin.getConfig().getDouble("visuals.render-distance", 72), squared = range * range;
        boolean viewer = false;
        for (Player player : location.getWorld().getPlayers()) {
            double dx = player.getX() - location.getX(), dz = player.getZ() - location.getZ();
            if (dx * dx + dz * dz <= squared) { viewer = true; break; }
        }
        if (!viewer || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) { removeVisual(caravan.id()); return; }
        location.setY(location.getWorld().getHighestBlockYAt(location.getBlockX(), location.getBlockZ()) + 1.0);
        Visual visual = visuals.get(caravan.id()); if (visual == null || !valid(visual)) visual = spawn(caravan, location);
        if (visual == null) return;
        visual.trader.teleport(location); visual.left.teleport(location.clone().add(1.4, 0, 0.8)); visual.right.teleport(location.clone().add(-1.4, 0, 0.8));
    }
    private Visual spawn(Caravan caravan, Location location) {
        Town from = towny.town(caravan.sellerId()), to = towny.town(caravan.buyerId());
        String name = ColorUtil.color(plugin.getConfig().getString("visuals.name", "&eКараван: %from% → %to%")
                .replace("%from%", from == null ? "?" : from.getName()).replace("%to%", to == null ? "?" : to.getName()));
        WanderingTrader trader = location.getWorld().spawn(location, WanderingTrader.class, entity -> setup(entity, name));
        TraderLlama left = location.getWorld().spawn(location.clone().add(1.4, 0, 0.8), TraderLlama.class, entity -> setup(entity, name));
        TraderLlama right = location.getWorld().spawn(location.clone().add(-1.4, 0, 0.8), TraderLlama.class, entity -> setup(entity, name));
        Visual visual = new Visual(trader, left, right); visuals.put(caravan.id(), visual); return visual;
    }
    private void setup(org.bukkit.entity.Mob entity, String name) {
        entity.setAI(false); entity.setSilent(true); entity.setInvulnerable(true); entity.setPersistent(false);
        entity.setCustomName(name); entity.setCustomNameVisible(entity instanceof WanderingTrader);
    }
    private Location position(Caravan caravan) {
        List<RoutePoint> points = caravan.route(); if (points.size() < 2) return null;
        double target = caravan.progress(System.currentTimeMillis());
        double total = 0; for (int i = 1; i < points.size(); i++) total += points.get(i - 1).distance(points.get(i));
        double remaining = total * target;
        for (int i = 1; i < points.size(); i++) {
            RoutePoint a = points.get(i - 1), b = points.get(i); double segment = a.distance(b);
            if (remaining <= segment || i == points.size() - 1) {
                double t = segment <= 0 ? 0 : Math.min(1, remaining / segment); World world = Bukkit.getWorld(a.worldId());
                return world == null ? null : new Location(world, a.x() + (b.x() - a.x()) * t,
                        a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t);
            }
            remaining -= segment;
        }
        return null;
    }
    private boolean valid(Visual visual) { return visual.trader.isValid() && visual.left.isValid() && visual.right.isValid(); }
    private void removeVisual(UUID id) { Visual visual = visuals.remove(id); if (visual != null) remove(visual); }
    private void remove(Visual visual) { for (Entity entity : List.of(visual.trader, visual.left, visual.right)) if (entity.isValid()) entity.remove(); }
    private record Visual(WanderingTrader trader, TraderLlama left, TraderLlama right) {}
}
