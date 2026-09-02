package ru.neverland.mintcamps.integration;

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
            plugin.getLogger().info("ItemsAdder обнаружен: пользовательские иконки лагерей включены.");
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("ItemsAdder найден, но API недоступен: " + exception.getMessage());
        }
    }

    public ItemStack item(String id, int amount) {
        if (!available || id == null || id.isBlank()) return null;
        try {
            Object custom = getInstance.invoke(null, id);
            if (custom == null) return null;
            ItemStack item = ((ItemStack) getItemStack.invoke(custom)).clone();
            item.setAmount(Math.max(1, amount));
            return item;
        } catch (ReflectiveOperationException | ClassCastException exception) {
            plugin.getLogger().warning("Не удалось получить предмет ItemsAdder " + id + ": " + exception.getMessage());
            return null;
        }
    }
}
