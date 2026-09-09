package ru.neverland.mintevents.listener;

import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.event.MobRemovalEvent;
import com.palmergames.bukkit.towny.event.mobs.MobSpawnRemovalEvent;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintevents.integration.TownyHook;
import ru.neverland.mintevents.model.ActiveEvent;
import ru.neverland.mintevents.model.EventDefinition;
import ru.neverland.mintevents.model.EventMode;
import ru.neverland.mintevents.service.EventService;

import java.util.concurrent.ThreadLocalRandom;

public final class EventGameplayListener implements Listener {
    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final EventService events;

    public EventGameplayListener(JavaPlugin plugin, TownyHook towny, EventService events) {
        this.plugin = plugin;
        this.towny = towny;
        this.events = events;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        Town town = towny.townAt(event.getBlock().getLocation());
        if (town == null) return;
        ActiveEvent active = events.active(town.getUUID());
        EventDefinition definition = events.definition(active);
        if (definition == null || definition.mode() != EventMode.DROUGHT) return;
        double base = plugin.getConfig().getDouble("gameplay.drought-base-crop-cancel-chance", 0.70);
        double chance = Math.max(0, Math.min(1, base * (1.0 - active.protection())));
        if (ThreadLocalRandom.current().nextDouble() < chance) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRaidProjectile(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Entity shooter && isRaidMob(shooter)) {
            event.setDamage(event.getDamage() * events.raidRangedMultiplier(shooter));
        }
    }

    @EventHandler
    public void onEntitiesLoad(org.bukkit.event.world.EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) events.validateLoadedRaidEntity(entity);
    }

    @EventHandler(ignoreCancelled = true)
    public void onRaidGrief(EntityChangeBlockEvent event) {
        if (isRaidMob(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onRaidExplosion(EntityExplodeEvent event) {
        if (isRaidMob(event.getEntity())) event.blockList().clear();
    }

    @EventHandler
    public void onRaidDeath(EntityDeathEvent event) {
        if (!isRaidMob(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        events.creditRaidKill(event.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTownySpawnRemoval(MobSpawnRemovalEvent event) {
        if (isRaidMob(event.getEntityOrNull())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTownyMobRemoval(MobRemovalEvent event) {
        if (isRaidMob(event.getEntity())) event.setCancelled(true);
    }

    private boolean isRaidMob(Entity entity) {
        return entity != null && events.isRaidMob(entity);
    }
}
