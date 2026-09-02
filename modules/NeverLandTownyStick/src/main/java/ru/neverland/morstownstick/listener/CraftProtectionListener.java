package ru.neverland.morstownstick.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.neverland.morstownstick.service.StickService;

import java.util.Set;

public final class CraftProtectionListener implements Listener {
    private static final Set<InventoryType> PROTECTED = Set.of(
            InventoryType.CRAFTING, InventoryType.WORKBENCH, InventoryType.ANVIL,
            InventoryType.SMITHING, InventoryType.GRINDSTONE, InventoryType.CARTOGRAPHY,
            InventoryType.STONECUTTER, InventoryType.LOOM
    );
    private final StickService sticks;

    public CraftProtectionListener(StickService sticks) {
        this.sticks = sticks;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent event) {
        if (containsSelector(event.getInventory())) event.getInventory().setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent event) {
        if (containsSelector(event.getInventory())) event.setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmithing(PrepareSmithingEvent event) {
        if (containsSelector(event.getInventory())) event.setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGrindstone(PrepareGrindstoneEvent event) {
        if (containsSelector(event.getInventory())) event.setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!PROTECTED.contains(top.getType())) return;
        boolean pointsAtTop = event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize();
        boolean shiftIntoTop = event.isShiftClick() && event.getClickedInventory() != top;
        ItemStack hotbar = event.getHotbarButton() >= 0 ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null;
        if ((pointsAtTop || shiftIntoTop) && (sticks.isSelector(event.getCursor())
                || sticks.isSelector(event.getCurrentItem()) || sticks.isSelector(hotbar))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!PROTECTED.contains(event.getView().getTopInventory().getType()) || !sticks.isSelector(event.getOldCursor())) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFuel(FurnaceBurnEvent event) {
        if (sticks.isSelector(event.getFuel())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAutomation(InventoryMoveItemEvent event) {
        if (sticks.isSelector(event.getItem())) event.setCancelled(true);
    }

    private boolean containsSelector(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) if (sticks.isSelector(item)) return true;
        return false;
    }
}
