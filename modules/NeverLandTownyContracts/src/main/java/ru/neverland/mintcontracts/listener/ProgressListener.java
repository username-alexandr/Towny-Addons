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
        contracts.record(event.getPlayer(), ContractType.BLOCK_BREAK, event.getBlock().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        String target = event.getCaught() instanceof Item item ? item.getItemStack().getType().name() : "ANY";
        contracts.record(event.getPlayer(), ContractType.FISH, target, 1);
    }
}
