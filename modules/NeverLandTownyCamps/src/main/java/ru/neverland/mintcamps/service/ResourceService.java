package ru.neverland.mintcamps.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ResourceService {
    public record Missing(ItemStack item, int required, int available) { }

    public List<Missing> missing(Player player, List<ItemStack> costs) {
        List<Missing> missing = new ArrayList<>();
        for (ItemStack cost : costs) {
            int available = count(player, cost);
            if (available < cost.getAmount()) missing.add(new Missing(cost.clone(), cost.getAmount(), available));
        }
        return missing;
    }

    public boolean withdraw(Player player, List<ItemStack> costs) {
        if (!missing(player, costs).isEmpty()) return false;
        for (ItemStack cost : costs) {
            int remaining = cost.getAmount();
            ItemStack[] storage = player.getInventory().getStorageContents();
            for (int slot = 0; slot < storage.length && remaining > 0; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack == null || !stack.isSimilar(cost)) continue;
                int remove = Math.min(remaining, stack.getAmount());
                remaining -= remove;
                if (remove == stack.getAmount()) player.getInventory().setItem(slot, null);
                else stack.setAmount(stack.getAmount() - remove);
            }
        }
        return true;
    }

    public void refund(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            for (ItemStack leftover : player.getInventory().addItem(item.clone()).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    public int count(Player player, ItemStack sample) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.isSimilar(sample)) count += item.getAmount();
        }
        return count;
    }
}
