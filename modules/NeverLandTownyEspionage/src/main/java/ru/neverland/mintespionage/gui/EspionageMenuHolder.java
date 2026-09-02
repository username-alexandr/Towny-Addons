package ru.neverland.mintespionage.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.UUID;

public final class EspionageMenuHolder implements InventoryHolder {
    public enum Type { MAIN, TARGETS, OPERATIONS, REPORTS, ACTIVE }
    private final UUID townId,targetId;private final Type type;private Inventory inventory;
    public EspionageMenuHolder(UUID townId,UUID targetId,Type type){this.townId=townId;this.targetId=targetId;this.type=type;}
    public UUID townId(){return townId;}public UUID targetId(){return targetId;}public Type type(){return type;}public void inventory(Inventory value){inventory=value;}
    @Override public Inventory getInventory(){return inventory;}
}
