package ru.neverland.reputation.integration;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

public final class ItemsAdderHook {
    private boolean available;
    public ItemsAdderHook() { reload(); }
    public void reload() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { available = Bukkit.getPluginManager().isPluginEnabled("ItemsAdder"); } }
    public ItemStack item(String namespacedId) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if (!available || namespacedId == null || namespacedId.isBlank()) return null;
        try {
            Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method getInstance = customStack.getMethod("getInstance", String.class);
            Object custom = getInstance.invoke(null, namespacedId);
            if (custom == null) return null;
            return ((ItemStack) customStack.getMethod("getItemStack").invoke(custom)).clone();
        } catch (ReflectiveOperationException | ClassCastException ignored) { return null; }
    } }
}
