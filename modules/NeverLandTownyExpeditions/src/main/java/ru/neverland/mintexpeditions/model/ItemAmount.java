package ru.neverland.mintexpeditions.model;
import org.bukkit.inventory.ItemStack;
public record ItemAmount(String key, ItemStack item, int amount) { public ItemStack stack() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { ItemStack copy = item.clone(); copy.setAmount(Math.min(copy.getMaxStackSize(), Math.max(1, amount))); return copy; } } }
