package ru.neverland.mintcontracts.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public final class WarehouseBridge {
    public enum Status { SUCCESS, UNAVAILABLE, BUSY, FULL }
    public record Deposit(Status status, int accepted) {}

    private final JavaPlugin plugin;
    private boolean warned;
    public WarehouseBridge(JavaPlugin plugin) { this.plugin = plugin; }

    public boolean available() { return resolve() != null; }

    public synchronized Deposit deposit(UUID townId, ItemStack sample, int requested) {
        Access access = resolve();
        if (access == null) return new Deposit(Status.UNAVAILABLE, 0);
        if (plugin.getConfig().getBoolean("warehouse.block-deposits-while-open", true) && isOpen(townId))
            return new Deposit(Status.BUSY, 0);
        try {
            Object townData = access.town.invoke(access.dataStore, townId);
            ItemStack[] storage = (ItemStack[]) access.storage.invoke(townData);
            int accepted = insert(storage, sample, requested);
            if (accepted <= 0) return new Deposit(Status.FULL, 0);
            access.setStorage.invoke(townData, storage, plugin.getConfig().getInt("warehouse.storage-size", 54));
            access.markDirty.invoke(access.dataStore);
            access.save.invoke(access.dataStore);
            return new Deposit(Status.SUCCESS, accepted);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            warn(exception);
            return new Deposit(Status.UNAVAILABLE, 0);
        }
    }

    private int insert(ItemStack[] contents, ItemStack sample, int requested) {
        int remaining = Math.max(0, requested);
        for (ItemStack existing : contents) {
            if (remaining <= 0) break;
            if (existing == null || !existing.isSimilar(sample)) continue;
            int space = existing.getMaxStackSize() - existing.getAmount();
            int add = Math.min(space, remaining);
            existing.setAmount(existing.getAmount() + add);
            remaining -= add;
        }
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            if (contents[slot] != null) continue;
            ItemStack added = sample.clone();
            int amount = Math.min(added.getMaxStackSize(), remaining);
            added.setAmount(amount);
            contents[slot] = added;
            remaining -= amount;
        }
        return requested - remaining;
    }

    private boolean isOpen(UUID townId) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder(false);
            if (holder == null || !holder.getClass().getName().endsWith("MenuManager$StorageHolder")) continue;
            try {
                Method method = holder.getClass().getDeclaredMethod("townId");
                method.setAccessible(true);
                if (townId.equals(method.invoke(holder))) return true;
            } catch (ReflectiveOperationException ignored) { return true; }
        }
        return false;
    }

    private Access resolve() {
        String name = plugin.getConfig().getString("warehouse.plugin", "NeverLandTownyBuilds");
        Plugin source = Bukkit.getPluginManager().getPlugin(name);
        if (source == null || !source.isEnabled()) return null;
        try {
            Field field = source.getClass().getDeclaredField("dataStore");
            field.setAccessible(true);
            Object store = field.get(source);
            Method town = store.getClass().getMethod("town", UUID.class);
            Method markDirty = store.getClass().getMethod("markDirty");
            Method save = store.getClass().getMethod("save");
            Class<?> townDataType = Class.forName("ru.neverland.townybuilds.data.TownData", false, source.getClass().getClassLoader());
            Method storage = townDataType.getMethod("storage");
            Method setStorage = townDataType.getMethod("setStorage", ItemStack[].class, int.class);
            return new Access(store, town, markDirty, save, storage, setStorage);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warn(exception);
            return null;
        }
    }

    private void warn(Exception exception) {
        if (warned) return;
        warned = true;
        plugin.getLogger().severe("Не удалось подключиться к складу /t inv: " + exception.getMessage());
    }
    private record Access(Object dataStore, Method town, Method markDirty, Method save, Method storage, Method setStorage) {}
}
