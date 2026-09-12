package ru.neverland.core;

import org.bukkit.inventory.ItemStack;

public final class InventoryPlan {
   private InventoryPlan() {
   }

   public static ItemStack[] insert(ItemStack[] contents, ItemStack cargo) {
      ItemStack[] result = new ItemStack[contents.length];

      for (int i = 0; i < contents.length; i++) {
         result[i] = contents[i] == null ? null : contents[i].clone();
      }

      int left = cargo.getAmount();

      for (int i = 0; i < result.length && left > 0; i++) {
         ItemStack item = result[i];
         if (item != null && !item.getType().isAir() && item.isSimilar(cargo)) {
            int n = Math.min(left, Math.max(0, item.getMaxStackSize() - item.getAmount()));
            item.setAmount(item.getAmount() + n);
            left -= n;
         }
      }

      for (int ix = 0; ix < result.length && left > 0; ix++) {
         if (result[ix] == null || result[ix].getType().isAir()) {
            ItemStack item = cargo.clone();
            int n = Math.min(left, item.getMaxStackSize());
            item.setAmount(n);
            result[ix] = item;
            left -= n;
         }
      }

      return left == 0 ? result : null;
   }
}
