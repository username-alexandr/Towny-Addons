package ru.neverland.mintcontracts.integration;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Method;

public final class ItemsAdderHook {
    private final JavaPlugin plugin;
    private Method getInstance;
    private Method getItemStack;
    private boolean available;
    public ItemsAdderHook(JavaPlugin plugin) { this.plugin = plugin; reload(); }
    public void reload() {
        available = false;
        if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return;
        try {
            Class<?> type = Class.forName("dev.lone.itemsadder.api.CustomStack");
            getInstance = type.getMethod("getInstance", String.class);
            getItemStack = type.getMethod("getItemStack");
            available = true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("API ItemsAdder недоступен: " + exception.getMessage());
        }
    }
    public ItemStack item(String id) {
        if (!available || id == null || id.isBlank()) return null;
        try {
            Object custom = getInstance.invoke(null, id);
            if (custom == null) return null;
            ItemStack stack = ((ItemStack) getItemStack.invoke(custom)).clone();
            stack.setAmount(1);
            return stack;
        } catch (ReflectiveOperationException | ClassCastException exception) { return null; }
    }
}
