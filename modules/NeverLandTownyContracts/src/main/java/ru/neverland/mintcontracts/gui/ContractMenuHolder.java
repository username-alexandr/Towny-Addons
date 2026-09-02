package ru.neverland.mintcontracts.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.UUID;

public final class ContractMenuHolder implements InventoryHolder {
    public enum Type { BOARD, HISTORY }
    private final UUID townId;
    private final Type type;
    private Inventory inventory;
    public ContractMenuHolder(UUID townId, Type type) { this.townId = townId; this.type = type; }
    public UUID townId() { return townId; }
    public Type type() { return type; }
    public void inventory(Inventory value) { inventory = value; }
    @Override public Inventory getInventory() { return inventory; }
}
