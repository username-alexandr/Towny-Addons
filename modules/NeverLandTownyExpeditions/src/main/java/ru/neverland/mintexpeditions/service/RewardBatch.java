package ru.neverland.mintexpeditions.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import ru.neverland.core.EffectJournal;

public record RewardBatch(UUID id, UUID owner, List<ItemStack> items, double money) {
   public RewardBatch(UUID id, UUID owner, List<ItemStack> items, double money) {
      Objects.requireNonNull(id);
      Objects.requireNonNull(owner);
      if (Double.isFinite(money) && !(money < 0.0) && !(money > 1.0E9)) {
         money = BigDecimal.valueOf(money).setScale(2, RoundingMode.HALF_UP).doubleValue();
         List<ItemStack> stacks = new ArrayList<>();

         for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || item.getAmount() < 1 || item.getAmount() > 1000000) {
               throw new IllegalArgumentException("Неверный предмет награды");
            }

            int left = item.getAmount();

            while (left > 0) {
               ItemStack stack = item.clone();
               stack.setAmount(Math.min(left, item.getMaxStackSize()));
               stacks.add(stack);
               left -= stack.getAmount();
            }
         }

         items = List.copyOf(stacks);
         this.id = id;
         this.owner = owner;
         this.items = items;
         this.money = money;
      } else {
         throw new IllegalArgumentException("Неверная награда");
      }
   }

   public List<ItemStack> items() {
      return this.items.stream().<ItemStack>map(ItemStack::clone).toList();
   }

   public UUID moneyId() {
      return EffectJournal.id("expedition:" + this.id + ":money");
   }

   public UUID itemId(int index) {
      return EffectJournal.id("expedition:" + this.id + ":item:" + index);
   }

   public Set<UUID> effects() {
      HashSet<UUID> ids = new HashSet<>();
      if (this.money > 0.0) {
         ids.add(this.moneyId());
      }

      for (int i = 0; i < this.items.size(); i++) {
         ids.add(this.itemId(i));
      }

      return ids;
   }
}
