package ru.neverland.minttrade.integration;

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
    public enum Status { SUCCESS, UNAVAILABLE, BUSY, INSUFFICIENT, FULL }
    public record Result(Status status, int amount) {}
    private final JavaPlugin plugin;
    private boolean warned;
    public WarehouseBridge(JavaPlugin plugin) { this.plugin = plugin; }
    public boolean available() { return resolve() != null; }
    public synchronized Result take(UUID townId, ItemStack sample, int requested) {
        Access access = resolve(); if (access == null) return new Result(Status.UNAVAILABLE, 0);
        if (busy(townId)) return new Result(Status.BUSY, 0);
        try {
            Object townData = access.town.invoke(access.store, townId);
            ItemStack[] storage = (ItemStack[]) access.storage.invoke(townData);
            int count = count(storage, sample);
            if (count < requested) return new Result(Status.INSUFFICIENT, count);
            remove(storage, sample, requested);
            persist(access, townData, storage);
            return new Result(Status.SUCCESS, requested);
        } catch (ReflectiveOperationException | ClassCastException exception) { warn(exception); return new Result(Status.UNAVAILABLE, 0); }
    }
    public synchronized Result deposit(UUID townId, ItemStack sample, int requested) {
        Access access = resolve(); if (access == null) return new Result(Status.UNAVAILABLE, 0);
        if (busy(townId)) return new Result(Status.BUSY, 0);
        try {
            Object townData = access.town.invoke(access.store, townId);
            ItemStack[] storage = (ItemStack[]) access.storage.invoke(townData);
            if (capacity(storage, sample) < requested) return new Result(Status.FULL, 0);
            int accepted = insert(storage, sample, requested);
            persist(access, townData, storage);
            return new Result(Status.SUCCESS, accepted);
        } catch (ReflectiveOperationException | ClassCastException exception) { warn(exception); return new Result(Status.UNAVAILABLE, 0); }
    }
    private void persist(Access access, Object townData, ItemStack[] storage) throws ReflectiveOperationException {
        access.setStorage.invoke(townData, storage, plugin.getConfig().getInt("warehouse.storage-size", 54));
        access.markDirty.invoke(access.store); access.save.invoke(access.store);
    }
    private int count(ItemStack[] contents, ItemStack sample) { int count = 0; for (ItemStack item : contents) if (item != null && item.isSimilar(sample)) count += item.getAmount(); return count; }
    private void remove(ItemStack[] contents, ItemStack sample, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot]; if (item == null || !item.isSimilar(sample)) continue;
            int take = Math.min(item.getAmount(), remaining); remaining -= take;
            if (take == item.getAmount()) contents[slot] = null; else item.setAmount(item.getAmount() - take);
        }
    }
    private int insert(ItemStack[] contents, ItemStack sample, int amount) {
        int remaining = amount;
        for (ItemStack item : contents) {
            if (remaining <= 0) break; if (item == null || !item.isSimilar(sample)) continue;
            int add = Math.min(item.getMaxStackSize() - item.getAmount(), remaining);
            item.setAmount(item.getAmount() + add); remaining -= add;
        }
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            if (contents[slot] != null) continue;
            ItemStack item = sample.clone(); int add = Math.min(item.getMaxStackSize(), remaining);
            item.setAmount(add); contents[slot] = item; remaining -= add;
        }
        return amount - remaining;
    }
    private int capacity(ItemStack[] contents, ItemStack sample) {
        int capacity = 0;
        for (ItemStack item : contents) {
            if (item == null) capacity += sample.getMaxStackSize();
            else if (item.isSimilar(sample)) capacity += item.getMaxStackSize() - item.getAmount();
        }
        return capacity;
    }
    private boolean busy(UUID townId) {
        Access access=resolve();if(access==null)return true;
        try { if(Boolean.TRUE.equals(access.store.getClass().getMethod("storageBusy",UUID.class,String.class).invoke(access.store,townId,"warehouse")))return true; }
        catch(ReflectiveOperationException ex){warn(ex);return true;}

        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder(false);
            if (holder == null || !holder.getClass().getName().endsWith("MenuManager$StorageHolder")) continue;
            try {
                Method townIdMethod = holder.getClass().getDeclaredMethod("townId"); townIdMethod.setAccessible(true);
                if (townId.equals(townIdMethod.invoke(holder))) return true;
            } catch (ReflectiveOperationException ignored) { return true; }
        }
        return false;
    }
    private Access resolve() {
        String name = plugin.getConfig().getString("warehouse.plugin", "NeverLandTownyBuilds");
        Plugin source = Bukkit.getPluginManager().getPlugin(name);
        if (source == null || !source.isEnabled()) return null;
        try {
            Field field = source.getClass().getDeclaredField("dataStore"); field.setAccessible(true);
            Object store = field.get(source);
            Method town = store.getClass().getMethod("town", UUID.class);
            Method markDirty = store.getClass().getMethod("markDirty"); Method save = store.getClass().getMethod("save");
            Class<?> dataType = Class.forName("ru.neverland.townybuilds.data.TownData", false, source.getClass().getClassLoader());
            return new Access(store, town, markDirty, save, dataType.getMethod("storage"),
                    dataType.getMethod("setStorage", ItemStack[].class, int.class));
        } catch (ReflectiveOperationException | RuntimeException exception) { warn(exception); return null; }
    }
    private void warn(Exception exception) { if (!warned) { warned = true; plugin.getLogger().severe("Не удалось подключиться к /t inv: " + exception.getMessage()); } }
    private record Access(Object store, Method town, Method markDirty, Method save, Method storage, Method setStorage) {}
}
