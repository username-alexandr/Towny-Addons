package ru.neverland.archaeology.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record ArchaeologyMenuHolder(Type type, UUID owner) implements InventoryHolder {
    public enum Type { MAIN, JOURNAL, MUSEUM, COLLECTIONS, WONDERS }
    @Override public @NotNull Inventory getInventory() { throw new UnsupportedOperationException(); }
}
