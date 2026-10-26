package ru.neverland.mintexpeditions.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SculkBloomEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import ru.neverland.mintexpeditions.model.ActiveExpedition;
import ru.neverland.mintexpeditions.model.BlockPos;
import ru.neverland.mintexpeditions.service.ExpeditionService;

public final class ExpeditionListener implements Listener {
    private final ExpeditionService service;

    public ExpeditionListener(ExpeditionService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void shieldUse(PlayerInteractEvent event) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyExpeditions")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        if (!event.getAction().isRightClick() || event.getItem() == null
                || event.getItem().getType() != org.bukkit.Material.SHIELD
                || !service.shieldDenied(event.getPlayer())) return;
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.getPlayer().clearActiveItem();
        service.warnShield(event.getPlayer());
    } }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyExpeditions")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        Block block = event.getClickedBlock();
        if (block == null) return;
        ActiveExpedition expedition =
                service.at(BlockPos.of(block.getLocation()), block.getWorld());
        if (expedition == null) return;
        event.setCancelled(true);
        service.objective(event.getPlayer(), BlockPos.of(block.getLocation()));
    } }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyExpeditions")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        BlockPos position = BlockPos.of(event.getBlock().getLocation());
        ActiveExpedition expedition = service.at(position, event.getBlock().getWorld());
        if (expedition == null) return;
        event.setCancelled(true);
        service.objective(event.getPlayer(), position);
    } }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyExpeditions")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        if (protectedAt(event.getBlock())) event.setCancelled(true);
    } }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void teleport(EntityTeleportEvent event) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyExpeditions")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        ActiveExpedition expedition = service.expeditionForMob(event.getEntity());
        if (expedition == null || !service.preventMobTeleportOutside()
                || service.insideMobArea(expedition, event.getTo())) return;
        event.setCancelled(true);
        service.relocateMobNextTick(event.getEntity());
    } }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyExpeditions")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        if (!service.protectMobFromEnvironment()
                || service.expeditionForMob(event.getEntity()) == null) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.FALL
                && cause != EntityDamageEvent.DamageCause.SUFFOCATION
                && cause != EntityDamageEvent.DamageCause.DROWNING
                && cause != EntityDamageEvent.DamageCause.VOID
                && cause != EntityDamageEvent.DamageCause.FLY_INTO_WALL) return;
        event.setCancelled(true);
        event.getEntity().setFallDistance(0);
        if (cause == EntityDamageEvent.DamageCause.SUFFOCATION
                || cause == EntityDamageEvent.DamageCause.DROWNING
                || cause == EntityDamageEvent.DamageCause.VOID) {
            service.relocateMobNextTick(event.getEntity());
        }
    } }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(EntityDeathEvent event) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyExpeditions")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        ActiveExpedition expedition = service.expeditionForMob(event.getEntity());
        if (expedition == null) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null || !service.participant(killer, expedition)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        service.mobDied(event.getEntity());
    } }

    @EventHandler(ignoreCancelled = true)
    public void change(EntityChangeBlockEvent event) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyExpeditions")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        if (protectedAt(event.getBlock())) event.setCancelled(true);
    } }

    @EventHandler(ignoreCancelled = true)
    public void explode(EntityExplodeEvent event) { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyExpeditions")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        event.blockList().removeIf(this::protectedAt);
    } }

    @EventHandler(ignoreCancelled = true)
    public void explode(BlockExplodeEvent event) { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyExpeditions")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        event.blockList().removeIf(this::protectedAt);
    } }

    @EventHandler(ignoreCancelled = true)
    public void flow(BlockFromToEvent event) { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyExpeditions")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        if (protectedAt(event.getBlock()) || protectedAt(event.getToBlock())) {
            event.setCancelled(true);
        }
    } }

    @EventHandler(ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent event) { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyExpeditions")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        if (event.getBlocks().stream().anyMatch(this::protectedAt)) event.setCancelled(true);
    } }

    @EventHandler(ignoreCancelled = true)
    public void piston(BlockPistonRetractEvent event) { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyExpeditions")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        if (event.getBlocks().stream().anyMatch(this::protectedAt)) event.setCancelled(true);
    } }

    private boolean protectedAt(Block block) {
        return service.at(BlockPos.of(block.getLocation()), block.getWorld()) != null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void spread(BlockSpreadEvent event) { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyExpeditions")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        // Catalyst growth would change terrain outside the saved structure footprint.
        if (event.getNewState().getType().name().startsWith("SCULK")
                && protectedAt(event.getSource())) event.setCancelled(true);
    } }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void bloom(SculkBloomEvent event) { try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyExpeditions")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        for (ActiveExpedition expedition : service.repository().active()) {
            if (service.insideMobArea(expedition, event.getBlock().getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    } }
    private boolean pausedMob(org.bukkit.entity.Entity entity){
        if(entity instanceof org.bukkit.entity.Projectile p && p.getShooter() instanceof org.bukkit.entity.Entity owner)entity=owner;
        var expedition=service.expeditionForMob(entity);return expedition!=null&&expedition.timer().paused();
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void pausedDamage(org.bukkit.event.entity.EntityDamageEvent event){ try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {
        if(pausedMob(event.getEntity()) || event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent hit && pausedMob(hit.getDamager()))event.setCancelled(true);
    } }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void pausedTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent event){ try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {if(pausedMob(event.getEntity()))event.setCancelled(true);} }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void pausedExplosion(org.bukkit.event.entity.EntityExplodeEvent event){ try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {if(pausedMob(event.getEntity()))event.setCancelled(true);} }

}
