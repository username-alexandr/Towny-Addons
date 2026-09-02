package ru.neverland.townyideologies.integration;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public final class ItemsAdderHook {
    private final JavaPlugin plugin;
    private boolean available;
    private Method getInstance;
    private Method getItemStack;

    public ItemsAdderHook(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        available = false;
        if (!plugin.getConfig().getBoolean("settings.itemsadder.enabled", true)
                || Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) return;
        try {
            Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
            getInstance = customStack.getMethod("getInstance", String.class);
            getItemStack = customStack.getMethod("getItemStack");
            available = true;
            plugin.getLogger().info("ItemsAdder обнаружен: пользовательские иконки идеологий включены.");
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("API ItemsAdder недоступен: " + exception.getMessage());
        }
    }

    public ItemStack item(String namespacedId, int amount) {
        if (!available || namespacedId == null || namespacedId.isBlank()) return null;
        try {
            Object custom = getInstance.invoke(null, namespacedId);
            if (custom == null) return null;
            ItemStack stack = ((ItemStack) getItemStack.invoke(custom)).clone();
            stack.setAmount(Math.max(1, amount));
            return stack;
        } catch (ReflectiveOperationException | ClassCastException exception) {
            plugin.getLogger().warning("Не удалось получить предмет ItemsAdder " + namespacedId + ": " + exception.getMessage());
            return null;
        }
    }
}
