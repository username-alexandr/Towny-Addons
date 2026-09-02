package ru.neverland.mintevents.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ru.neverland.mintevents.model.ContributionRule;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EventMenuHolder implements InventoryHolder {
    public enum Type { MAIN, HISTORY }

    private final UUID townId;
    private final Type type;
    private final Map<Integer, ContributionRule> rules = new HashMap<>();
    private Inventory inventory;

    public EventMenuHolder(UUID townId, Type type) {
        this.townId = townId;
        this.type = type;
    }

    public UUID townId() { return townId; }
    public Type type() { return type; }
    public Map<Integer, ContributionRule> rules() { return rules; }
    public void inventory(Inventory value) { inventory = value; }
    @Override public Inventory getInventory() { return inventory; }
}
