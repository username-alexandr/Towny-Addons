package ru.neverland.mintevents.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ru.neverland.mintevents.model.ContributionRule;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EventMenuHolder implements InventoryHolder {
    public enum Type { MAIN, HISTORY, REPAIRS }

    private final UUID townId;
    private final Type type;
    private final Map<Integer, ContributionRule> rules = new HashMap<>();
    private final Map<Integer, UUID> repairs = new HashMap<>();
    private int page;
    private Inventory inventory;

    public EventMenuHolder(UUID townId, Type type) {
        this.townId = townId;
        this.type = type;
    }

    public UUID townId() { synchronized (ru.neverland.core.SuiteState.lock()) { return townId; } }
    public Type type() { synchronized (ru.neverland.core.SuiteState.lock()) { return type; } }
    public Map<Integer, ContributionRule> rules() { synchronized (ru.neverland.core.SuiteState.lock()) { return rules; } }
    public Map<Integer, UUID> repairs() { synchronized (ru.neverland.core.SuiteState.lock()) { return repairs; } }
    public int page() { synchronized (ru.neverland.core.SuiteState.lock()) { return page; } }
    public void page(int value) { synchronized (ru.neverland.core.SuiteState.lock()) { page = value; } }
    public void inventory(Inventory value) { synchronized (ru.neverland.core.SuiteState.lock()) { inventory = value; } }
    @Override public Inventory getInventory() { synchronized (ru.neverland.core.SuiteState.lock()) { return inventory; } }
}
