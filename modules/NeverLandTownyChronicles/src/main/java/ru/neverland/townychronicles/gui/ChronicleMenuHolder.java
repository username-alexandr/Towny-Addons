package ru.neverland.townychronicles.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ru.neverland.townychronicles.model.ChronicleCategory;
import java.util.UUID;

public final class ChronicleMenuHolder implements InventoryHolder {
    private final UUID townId;private final ChronicleCategory category;private final int page;private Inventory inventory;
    public ChronicleMenuHolder(UUID townId,ChronicleCategory category,int page){this.townId=townId;this.category=category;this.page=page;}public UUID townId(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return townId;} }public ChronicleCategory category(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return category;} }public int page(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return page;} }public void inventory(Inventory value){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {inventory=value;} }@Override public Inventory getInventory(){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {return inventory;} }
}
