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
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    public ProgressListener(org.bukkit.plugin.java.JavaPlugin plugin, ContractService contracts) { this.plugin=plugin;this.contracts = contracts; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyContracts")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        Player killer = event.getEntity().getKiller();
        String target=event.getEntityType().name();
        if (killer != null) ru.neverland.earnings.HuntingCredit.afterDeath(plugin,event,killer,
                ()->contracts.record(killer,ContractType.MOB_KILL,target,1));
    } }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyContracts")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        contracts.fieldWork().broken(event.getBlock());
        contracts.record(event.getPlayer(), ContractType.BLOCK_BREAK, event.getBlock().getType().name(), 1);
    } }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent e){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {contracts.fieldWork().placed(e.getPlayer(),e.getBlockPlaced());} }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onMove(org.bukkit.event.player.PlayerMoveEvent e){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {if(!(e instanceof org.bukkit.event.player.PlayerTeleportEvent))contracts.fieldWork().move(e.getPlayer(),e.getFrom(),e.getTo());} }
    @EventHandler(priority=EventPriority.MONITOR)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent e){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {contracts.fieldWork().reset(e.getPlayer());} }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onExplosion(org.bukkit.event.entity.EntityExplodeEvent e){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {e.blockList().forEach(contracts.fieldWork()::broken);} }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onBlockExplosion(org.bukkit.event.block.BlockExplodeEvent e){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {e.blockList().forEach(contracts.fieldWork()::broken);} }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onPiston(org.bukkit.event.block.BlockPistonExtendEvent e){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {e.getBlocks().forEach(contracts.fieldWork()::broken);} }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onRetract(org.bukkit.event.block.BlockPistonRetractEvent e){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {e.getBlocks().forEach(contracts.fieldWork()::broken);} }
    @EventHandler public void onQuit(org.bukkit.event.player.PlayerQuitEvent e){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {contracts.fieldWork().reset(e.getPlayer());} }
    @EventHandler public void onWorld(org.bukkit.event.player.PlayerChangedWorldEvent e){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {contracts.fieldWork().reset(e.getPlayer());} }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyContracts")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if(!ru.neverland.mintcontracts.fishing.FishingProtection.allows(plugin,event))return;
        if(!(event.getCaught() instanceof Item item)||!ru.neverland.core.SuiteScheduler.owns(item))return;
        String target=item.getItemStack().getType().name();
        if(!java.util.Set.of("COD","SALMON","PUFFERFISH","TROPICAL_FISH").contains(target))return;
        ru.neverland.core.SuiteScheduler.entity(plugin,event.getPlayer(),()->{if(!event.isCancelled())contracts.record(event.getPlayer(),ContractType.FISH,target,1);});
    } }
}
