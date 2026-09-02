package ru.neverland.governance.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.UUID;

public final class GovernanceMenuHolder implements InventoryHolder {
    public enum Type { MAIN, LAWS, VOTES, COUNCIL, HISTORY }
    private final Type type; private final UUID townId;
    public GovernanceMenuHolder(Type type, UUID townId) { this.type = type; this.townId = townId; }
    public Type type() { return type; }
    public UUID townId() { return townId; }
    @Override public Inventory getInventory() { return null; }
}
