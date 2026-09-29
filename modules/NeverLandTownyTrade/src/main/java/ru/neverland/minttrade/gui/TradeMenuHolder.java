package ru.neverland.minttrade.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.UUID;

public final class TradeMenuHolder implements InventoryHolder {
    public enum Type { BOARD, HISTORY }
    private final UUID townId; private final Type type; private Inventory inventory;
    public TradeMenuHolder(UUID townId, Type type) { this.townId = townId; this.type = type; }
    public UUID townId() { synchronized (ru.neverland.core.SuiteState.lock()) { return townId; } } public Type type() { synchronized (ru.neverland.core.SuiteState.lock()) { return type; } }
    public void inventory(Inventory value) { synchronized (ru.neverland.core.SuiteState.lock()) { inventory = value; } }
    @Override public Inventory getInventory() { synchronized (ru.neverland.core.SuiteState.lock()) { return inventory; } }
}
