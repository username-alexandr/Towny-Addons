package ru.neverland.townychronicles.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ru.neverland.townychronicles.model.ChronicleCategory;
import java.util.UUID;

public final class ChronicleMenuHolder implements InventoryHolder {
    private final UUID townId;private final ChronicleCategory category;private final int page;private Inventory inventory;
    public ChronicleMenuHolder(UUID townId,ChronicleCategory category,int page){this.townId=townId;this.category=category;this.page=page;}public UUID townId(){return townId;}public ChronicleCategory category(){return category;}public int page(){return page;}public void inventory(Inventory value){inventory=value;}@Override public Inventory getInventory(){return inventory;}
}
