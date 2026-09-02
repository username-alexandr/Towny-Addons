package ru.neverland.reputation.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import ru.neverland.reputation.model.ReputationScope;

import java.util.UUID;

public record ReputationMenuHolder(Type type, ReputationScope scope, UUID owner) implements InventoryHolder {
    public enum Type { MAIN, RELATIONS, HISTORY, TOP, LEVELS }
    @Override public @NotNull Inventory getInventory() { throw new UnsupportedOperationException(); }
}
