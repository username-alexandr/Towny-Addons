package ru.neverland.townychronicles.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ru.neverland.townychronicles.model.ChronicleCategory;
import java.util.UUID;

public final class ChronicleMenuHolder implements InventoryHolder {
    private final UUID townId;private final ChronicleCategory category;private final int page;private Inventory inventory;
    public ChronicleMenuHolder(UUID townId,ChronicleCategory category,int page){this.townId=townId;this.category=category;this.page=page;}public UUID townId(){ synchronized (ru.neverland.core.SuiteState.lock()) {return townId;} }public ChronicleCategory category(){ synchronized (ru.neverland.core.SuiteState.lock()) {return category;} }public int page(){ synchronized (ru.neverland.core.SuiteState.lock()) {return page;} }public void inventory(Inventory value){ synchronized (ru.neverland.core.SuiteState.lock()) {inventory=value;} }@Override public Inventory getInventory(){ synchronized (ru.neverland.core.SuiteState.lock()) {return inventory;} }
}
