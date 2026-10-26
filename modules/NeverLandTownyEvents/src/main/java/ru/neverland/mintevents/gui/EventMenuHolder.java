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

    public UUID townId() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return townId; } }
    public Type type() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return type; } }
    public Map<Integer, ContributionRule> rules() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return rules; } }
    public Map<Integer, UUID> repairs() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return repairs; } }
    public int page() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return page; } }
    public void page(int value) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { page = value; } }
    public void inventory(Inventory value) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { inventory = value; } }
    @Override public Inventory getInventory() { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { return inventory; } }
}
