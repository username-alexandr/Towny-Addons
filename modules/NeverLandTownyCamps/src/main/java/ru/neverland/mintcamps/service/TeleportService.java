package ru.neverland.mintcamps.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.mintcamps.data.CampRepository;
import ru.neverland.mintcamps.model.Camp;
import ru.neverland.mintcamps.util.TimeUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TeleportService implements Listener {
    private final JavaPlugin plugin;
    private final CampRepository repository;
    private final MessageService messages;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public TeleportService(JavaPlugin plugin, CampRepository repository, MessageService messages) {
        this.plugin = plugin;
        this.repository = repository;
        this.messages = messages;
    }

    public void teleport(Player player, String ownerName) { synchronized (ru.neverland.core.SuiteState.lock()) {
        Camp camp = ownerName == null || ownerName.isBlank()
                ? repository.get(player.getUniqueId()).orElse(null)
                : repository.getByOwnerName(ownerName).orElse(null);
        if (camp == null) {
            messages.send(player, ownerName == null || ownerName.isBlank() ? "no-camp" : "teleport.target-not-found",
                    Map.of("owner", ownerName == null ? "" : ownerName));
            return;
        }
        if (!camp.ownerId().equals(player.getUniqueId())
                && !plugin.getConfig().getBoolean("settings.teleport.allow-trusted-camps", true)) {
            messages.send(player, "teleport.no-access", Map.of("owner", camp.ownerName()));
            return;
        }
        if (!camp.canAccess(player.getUniqueId())) {
            messages.send(player, "teleport.no-access", Map.of("owner", camp.ownerName()));
            return;
        }
        long now = System.currentTimeMillis();
        long cooldown = cooldowns.getOrDefault(player.getUniqueId(), 0L) - now;
        if (cooldown > 0 && !player.hasPermission("mintcamps.admin")) {
            messages.send(player, "teleport.cooldown", Map.of("time", TimeUtil.formatMillis(cooldown)));
            return;
        }
        Location target = camp.anchorLocation();
        if (target == null) {
            messages.send(player, "teleport.unsafe");
            return;
        }
        cancel(player, null);
        int seconds = Math.max(0, plugin.getConfig().getInt("settings.teleport.warmup-seconds", 3));
        if (seconds == 0) {
            complete(player, target, camp);
            return;
        }
        messages.send(player, "teleport.warmup", Map.of("seconds", seconds));
        play(player, "settings.sounds.teleport-start", Sound.BLOCK_BEACON_POWER_SELECT);
        BukkitTask task = ru.neverland.core.SuiteScheduler.entityLater(plugin, player, () -> {
            pending.remove(player.getUniqueId());
            if (player.isOnline()) complete(player, target, camp);
        }, () -> pending.remove(player.getUniqueId()), seconds * 20L);
        pending.put(player.getUniqueId(), new Pending(player.getLocation().clone(), task));
    } }

    public void cancelAll() { synchronized (ru.neverland.core.SuiteState.lock()) {
        for (Pending value : pending.values()) value.task().cancel();
        pending.clear();
    } }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) { synchronized (ru.neverland.core.SuiteState.lock()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyCamps")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        Pending value = pending.get(event.getPlayer().getUniqueId());
        if (value == null || !plugin.getConfig().getBoolean("settings.teleport.cancel-on-move", true)) return;
        Location to = event.getTo();
        if (to != null && (to.getWorld() != value.start().getWorld()
                || to.distanceSquared(value.start()) > 0.04D)) {
            cancel(event.getPlayer(), "teleport.cancelled-move");
        }
    } }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) { synchronized (ru.neverland.core.SuiteState.lock()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyCamps")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        if (event.getEntity() instanceof Player player && pending.containsKey(player.getUniqueId())) {
            cancel(player, "teleport.cancelled-damage");
        }
    } }

    private void complete(Player player, Location target, Camp camp) {
        ru.neverland.core.SuiteScheduler.region(plugin, target, () -> {
            Location safe = safeLocation(camp);
            ru.neverland.core.SuiteScheduler.entity(plugin, player, () -> {
                if (!player.isOnline() || repository.get(camp.ownerId()).orElse(null) != camp || !camp.canAccess(player.getUniqueId())) return;
                if (safe == null) { messages.send(player, "teleport.unsafe"); return; }
                teleportChecked(player, safe, camp);
            });
        });
    }
    private void teleportChecked(Player player, Location target, Camp camp) {
        player.teleportAsync(target).thenAccept(success -> ru.neverland.core.SuiteScheduler.entity(plugin, player, () -> {
            if (!success || !player.isOnline()) return;
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis()
                    + plugin.getConfig().getLong("settings.teleport.cooldown-seconds", 30L) * 1000L);
            messages.send(player, "teleport.success", Map.of("owner", camp.ownerName()));
            play(player, "settings.sounds.teleport-complete", Sound.ENTITY_ENDERMAN_TELEPORT);
        }));
    }

    private Location safeLocation(Camp camp) {
        Location anchor = camp.anchorLocation();
        if (anchor == null) return null;
        int[][] offsets = {{-2, 0}, {2, 0}, {0, -2}, {-3, -2}, {3, -2}, {0, -3}, {-4, 0}, {4, 0}};
        for (int[] offset : offsets) {
            for (int dy = 3; dy >= -2; dy--) {
                if (!org.bukkit.Bukkit.isOwnedByCurrentRegion(anchor.getWorld(), (anchor.getBlockX() + offset[0]) >> 4, (anchor.getBlockZ() + offset[1]) >> 4)) continue;
                Block feet = anchor.getWorld().getBlockAt(anchor.getBlockX() + offset[0], anchor.getBlockY() + dy, anchor.getBlockZ() + offset[1]);
                if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, -1, 0).getType().isSolid()) {
                    return feet.getLocation().add(0.5D, 0.05D, 0.5D);
                }
            }
        }
        return null;
    }

    private void cancel(Player player, String message) {
        Pending value = pending.remove(player.getUniqueId());
        if (value != null) value.task().cancel();
        if (message != null) messages.send(player, message);
    }

    private void play(Player player, String path, Sound fallback) {
        Sound sound;
        try { sound = Sound.valueOf(plugin.getConfig().getString(path, fallback.name())); }
        catch (IllegalArgumentException exception) { sound = fallback; }
        player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
    }

    private record Pending(Location start, BukkitTask task) { }
}
