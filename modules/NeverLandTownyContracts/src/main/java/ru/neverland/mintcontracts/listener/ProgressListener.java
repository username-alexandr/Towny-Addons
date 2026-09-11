package ru.neverland.mintcontracts.listener;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import ru.neverland.mintcontracts.model.ContractType;
import ru.neverland.mintcontracts.service.ContractService;

public final class ProgressListener implements Listener {
    private final ContractService contracts;
    public ProgressListener(ContractService contracts) { this.contracts = contracts; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) contracts.record(killer, ContractType.MOB_KILL, event.getEntityType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        contracts.fieldWork().broken(event.getBlock());
        contracts.record(event.getPlayer(), ContractType.BLOCK_BREAK, event.getBlock().getType().name(), 1);
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent e){contracts.fieldWork().placed(e.getPlayer(),e.getBlockPlaced());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onMove(org.bukkit.event.player.PlayerMoveEvent e){if(!(e instanceof org.bukkit.event.player.PlayerTeleportEvent))contracts.fieldWork().move(e.getPlayer(),e.getFrom(),e.getTo());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent e){contracts.fieldWork().reset(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onExplosion(org.bukkit.event.entity.EntityExplodeEvent e){e.blockList().forEach(contracts.fieldWork()::broken);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onBlockExplosion(org.bukkit.event.block.BlockExplodeEvent e){e.blockList().forEach(contracts.fieldWork()::broken);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onPiston(org.bukkit.event.block.BlockPistonExtendEvent e){e.getBlocks().forEach(contracts.fieldWork()::broken);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onRetract(org.bukkit.event.block.BlockPistonRetractEvent e){e.getBlocks().forEach(contracts.fieldWork()::broken);}
    @EventHandler public void onQuit(org.bukkit.event.player.PlayerQuitEvent e){contracts.fieldWork().reset(e.getPlayer());}
    @EventHandler public void onWorld(org.bukkit.event.player.PlayerChangedWorldEvent e){contracts.fieldWork().reset(e.getPlayer());}

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        String target = event.getCaught() instanceof Item item ? item.getItemStack().getType().name() : "ANY";
        contracts.record(event.getPlayer(), ContractType.FISH, target, 1);
    }
}
