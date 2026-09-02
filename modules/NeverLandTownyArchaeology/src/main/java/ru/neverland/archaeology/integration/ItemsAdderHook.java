package ru.neverland.archaeology.integration;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

public final class ItemsAdderHook {
    private boolean available; public ItemsAdderHook() { reload(); } public void reload() { available = Bukkit.getPluginManager().isPluginEnabled("ItemsAdder"); }
    public ItemStack item(String id) { if (!available || id == null || id.isBlank()) return null; try { Class<?> type = Class.forName("dev.lone.itemsadder.api.CustomStack"); Object custom = type.getMethod("getInstance", String.class).invoke(null, id); if (custom == null) return null; return ((ItemStack) type.getMethod("getItemStack").invoke(custom)).clone(); } catch (ReflectiveOperationException | ClassCastException ignored) { return null; } }
}
