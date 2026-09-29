package ru.neverland.mintespionage.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.UUID;

public final class EspionageMenuHolder implements InventoryHolder {
    public enum Type { MAIN, TARGETS, OPERATIONS, REPORTS, ACTIVE }
    private final UUID townId,targetId;private final Type type;private Inventory inventory;
    public EspionageMenuHolder(UUID townId,UUID targetId,Type type){this.townId=townId;this.targetId=targetId;this.type=type;}
    public UUID townId(){ synchronized (ru.neverland.core.SuiteState.lock()) {return townId;} }public UUID targetId(){ synchronized (ru.neverland.core.SuiteState.lock()) {return targetId;} }public Type type(){ synchronized (ru.neverland.core.SuiteState.lock()) {return type;} }public void inventory(Inventory value){ synchronized (ru.neverland.core.SuiteState.lock()) {inventory=value;} }
    @Override public Inventory getInventory(){ synchronized (ru.neverland.core.SuiteState.lock()) {return inventory;} }
}
