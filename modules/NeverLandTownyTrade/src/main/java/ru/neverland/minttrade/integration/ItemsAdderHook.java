package ru.neverland.minttrade.integration;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import java.lang.reflect.Method;

public final class ItemsAdderHook {
    private boolean available;
    public ItemsAdderHook() { reload(); }
    public void reload() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { available = Bukkit.getPluginManager().isPluginEnabled("ItemsAdder"); } }
    public ItemStack item(String key) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if (!available || key == null || !key.toLowerCase().startsWith("itemsadder:")) return null;
        try {
            String namespaced = key.substring("itemsadder:".length());
            Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method getInstance = customStack.getMethod("getInstance", String.class);
            Object value = getInstance.invoke(null, namespaced);
            if (value == null) return null;
            return (ItemStack) customStack.getMethod("getItemStack").invoke(value);
        } catch (ReflectiveOperationException | ClassCastException ignored) { return null; }
    } }
}
