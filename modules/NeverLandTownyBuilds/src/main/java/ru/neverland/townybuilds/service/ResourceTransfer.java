package ru.neverland.townybuilds.service;

import org.bukkit.inventory.ItemStack;

/** Точная работа с обычной частью инвентаря без изменения брони и второй руки. */
public final class ResourceTransfer {
    private ResourceTransfer() {
    }

    public static int count(ItemStack[] contents, ItemStack required) {
        int amount = 0;
        for (ItemStack item : contents) {
            if (ResourceMatcher.matches(item, required)) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

    /**
     * @return количество, которое не удалось удалить
     */
    public static int remove(ItemStack[] contents, ItemStack required, int amount) {
        int remaining = amount;
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack item = contents[index];
            if (!ResourceMatcher.matches(item, required)) continue;
            int removed = Math.min(item.getAmount(), remaining);
            remaining -= removed;
            if (item.getAmount() == removed) {
                contents[index] = null;
            } else {
                item.setAmount(item.getAmount() - removed);
            }
        }
        return remaining;
    }
}
