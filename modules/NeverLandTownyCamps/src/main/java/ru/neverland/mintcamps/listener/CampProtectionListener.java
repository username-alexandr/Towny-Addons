package ru.neverland.mintcamps.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.mintcamps.data.CampRepository;
import ru.neverland.mintcamps.model.Camp;
import ru.neverland.mintcamps.service.MessageService;

import java.util.Iterator;

public final class CampProtectionListener implements Listener {
    private final JavaPlugin plugin;
    private final CampRepository repository;
    private final MessageService messages;
    private BukkitTask barrierTask;

    public CampProtectionListener(JavaPlugin plugin, CampRepository repository, MessageService messages) {
        this.plugin = plugin;
        this.repository = repository;
        this.messages = messages;
    }

    public void start() {
        stop();
        barrierTask = Bukkit.getScheduler().runTaskTimer(plugin, this::purgeHostiles, 20L, 20L);
    }

    public void stop() {
        if (barrierTask != null) barrierTask.cancel();
        barrierTask = null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (protect(event.getBlock(), event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (protect(event.getBlock(), event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent event) {
        if (protect(event.getBlock(), event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent event) {
        if (protect(event.getBlock(), event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!enabled("prevent-explosions")) return;
        event.blockList().removeIf(block -> repository.findAt(block.getLocation()).isPresent());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!enabled("prevent-explosions")) return;
        event.blockList().removeIf(block -> repository.findAt(block.getLocation()).isPresent());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (enabled("prevent-fire") && repository.findAt(event.getBlock().getLocation()).isPresent()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (enabled("prevent-fire") && repository.findAt(event.getBlock().getLocation()).isPresent()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (enabled("prevent-fire") && repository.findAt(event.getBlock().getLocation()).isPresent()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (enabled("prevent-block-changes") && repository.findAt(event.getToBlock().getLocation()).isPresent()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent event) {
        if (!enabled("prevent-block-changes")) return;
        for (Block block : event.getBlocks()) if (repository.findAt(block.getLocation()).isPresent()
                || repository.findAt(block.getRelative(event.getDirection()).getLocation()).isPresent()) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent event) {
        if (!enabled("prevent-block-changes")) return;
        for (Block block : event.getBlocks()) if (repository.findAt(block.getLocation()).isPresent()) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player) || !hostile(event.getEntity())) return;
        Camp camp = repository.findAt(player.getLocation()).orElse(null);
        if (activeBarrier(camp) && repository.findAt(event.getEntity().getLocation()).orElse(null) != camp) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Entity attacker = event.getDamager();
        if (attacker instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Entity entity) attacker = entity;
        }
        if (!hostile(attacker)) return;
        Camp camp = repository.findAt(player.getLocation()).orElse(null);
        if (activeBarrier(camp) && repository.findAt(attacker.getLocation()).orElse(null) != camp) event.setCancelled(true);
    }

    private boolean protect(Block block, Player player) {
        if (!enabled("prevent-block-changes") || player.hasPermission("mintcamps.bypass.protection")) return false;
        if (repository.findAt(block.getLocation()).isEmpty()) return false;
        messages.send(player, "protection.block-change");
        return true;
    }

    private void purgeHostiles() {
        if (!plugin.getConfig().getBoolean("settings.protection.enabled", true)) return;
        long now = System.currentTimeMillis();
        for (Camp camp : repository.all()) {
            if (!camp.mobsDisabled() || camp.burnUntil() <= now) continue;
            Location center = camp.anchorLocation();
            if (center == null || !center.getWorld().isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) continue;
            int radius = plugin.getConfig().getInt("settings.levels." + camp.level() + ".radius", 6);
            Iterator<Entity> iterator = center.getWorld().getNearbyEntities(center, radius, radius + 8, radius).iterator();
            while (iterator.hasNext()) {
                Entity entity = iterator.next();
                if (!(entity instanceof LivingEntity living) || !hostile(living)) continue;
                if (plugin.getConfig().getBoolean("settings.protection.holy-fire-particles", true)) {
                    living.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, living.getLocation().add(0, 1, 0), 18, .35, .55, .35, .025);
                }
                if (plugin.getConfig().getBoolean("settings.protection.holy-fire-sound", true)) {
                    living.getWorld().playSound(living.getLocation(), Sound.ENTITY_BLAZE_HURT, .65F, 1.45F);
                }
                if (plugin.getConfig().getBoolean("settings.protection.instant-kill-hostile-mobs", true)) living.remove();
                else living.damage(plugin.getConfig().getDouble("settings.protection.hostile-mob-damage", 1000D));
            }
        }
    }

    private boolean activeBarrier(Camp camp) {
        return camp != null && camp.mobsDisabled() && camp.burnUntil() > System.currentTimeMillis()
                && plugin.getConfig().getBoolean("settings.protection.enabled", true);
    }

    private boolean hostile(Entity entity) {
        return entity instanceof Enemy;
    }

    private boolean enabled(String path) {
        return plugin.getConfig().getBoolean("settings.protection.enabled", true)
                && plugin.getConfig().getBoolean("settings.protection." + path, true);
    }
}
