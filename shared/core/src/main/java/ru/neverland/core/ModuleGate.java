package ru.neverland.core;
public final class ModuleGate {
    private ModuleGate() {}
    public static void stopEvent(Object event) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        if(event instanceof org.bukkit.event.inventory.InventoryInteractEvent interaction) {
            var holder=interaction.getView().getTopInventory().getHolder();
            if(holder!=null&&holder.getClass().getName().startsWith("ru.neverland."))interaction.setCancelled(true);
        }
    } }
}
