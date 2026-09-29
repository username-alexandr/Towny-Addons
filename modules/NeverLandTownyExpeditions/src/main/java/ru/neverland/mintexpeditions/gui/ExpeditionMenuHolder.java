package ru.neverland.mintexpeditions.gui;
import org.bukkit.inventory.Inventory; import org.bukkit.inventory.InventoryHolder;
public final class ExpeditionMenuHolder implements InventoryHolder { public Inventory getInventory(){ synchronized (ru.neverland.core.SuiteState.lock()) {return null;} } }
