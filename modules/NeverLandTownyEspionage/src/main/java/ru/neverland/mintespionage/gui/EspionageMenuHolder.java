package ru.neverland.mintespionage.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.UUID;

public final class EspionageMenuHolder implements InventoryHolder {
    public enum Type { MAIN, TARGETS, OPERATIONS, REPORTS, ACTIVE, REPORT_DETAIL, ALERTS, PURCHASES }
    private final UUID townId,targetId;private final Type type;private Inventory inventory;
    public EspionageMenuHolder(UUID townId,UUID targetId,Type type){this.townId=townId;this.targetId=targetId;this.type=type;}
    public UUID townId(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return townId;} }public UUID targetId(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return targetId;} }public Type type(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return type;} }public void inventory(Inventory value){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {inventory=value;} }
    @Override public Inventory getInventory(){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {return inventory;} }
}
