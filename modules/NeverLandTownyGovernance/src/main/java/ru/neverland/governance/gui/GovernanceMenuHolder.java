package ru.neverland.governance.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.UUID;

public final class GovernanceMenuHolder implements InventoryHolder {
    public enum Type { MAIN, LAWS, VOTES, COUNCIL, HISTORY, OFFICE, RESIDENTS, OFFICE_CONFIRM, OFFICE_HOLDER, LAW_DETAIL, VOTE_DETAIL }
    private final Type type; private final UUID townId;
    private UUID viewer;
    private boolean queued;
    private final java.util.Map<Integer, java.util.function.Consumer<org.bukkit.entity.Player>> actions = new java.util.HashMap<>();
    public GovernanceMenuHolder(Type type, UUID townId) { this.type = type; this.townId = townId; }
    public GovernanceMenuHolder(Type type, UUID townId, UUID viewer) { this(type, townId); this.viewer = viewer; }
    public UUID viewer() { return viewer; }
    public void action(int slot, java.util.function.Consumer<org.bukkit.entity.Player> action) { actions.put(slot, action); }
    /** At most one transition or confirmation can leave this inventory. */
    public java.util.function.Consumer<org.bukkit.entity.Player> takeAction(int slot) {
        if (queued) return null;
        var action = actions.get(slot); if (action != null) queued = true; return action;
    }
    public Type type() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return type; } }
    public UUID townId() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return townId; } }
    @Override public Inventory getInventory() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return null; } }
}
