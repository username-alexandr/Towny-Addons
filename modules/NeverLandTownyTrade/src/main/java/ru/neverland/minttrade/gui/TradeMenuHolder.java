package ru.neverland.minttrade.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.UUID;

public final class TradeMenuHolder implements InventoryHolder {
    public enum Type { BOARD, HISTORY }
    private final UUID townId; private final Type type; private Inventory inventory;
    public TradeMenuHolder(UUID townId, Type type) { this.townId = townId; this.type = type; }
    public UUID townId() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return townId; } } public Type type() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return type; } }
    public void inventory(Inventory value) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { inventory = value; } }
    @Override public Inventory getInventory() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return inventory; } }
}
