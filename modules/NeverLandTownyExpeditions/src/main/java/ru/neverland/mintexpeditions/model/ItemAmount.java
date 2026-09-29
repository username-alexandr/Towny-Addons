package ru.neverland.mintexpeditions.model;
import org.bukkit.inventory.ItemStack;
public record ItemAmount(String key, ItemStack item, int amount) { public ItemStack stack() { synchronized (ru.neverland.core.SuiteState.lock()) { ItemStack copy = item.clone(); copy.setAmount(Math.min(copy.getMaxStackSize(), Math.max(1, amount))); return copy; } } }
