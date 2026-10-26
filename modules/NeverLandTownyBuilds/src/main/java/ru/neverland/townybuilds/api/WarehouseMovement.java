package ru.neverland.townybuilds.api;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public record WarehouseMovement(UUID id, ItemStack sample, int amount, boolean incoming) {
   public WarehouseMovement(UUID id, ItemStack sample, int amount, boolean incoming) {
      if (id != null && sample != null && !sample.getType().isAir() && amount >= 1 && amount <= 1000000) {
         sample = sample.clone();
         sample.setAmount(1);
         this.id = id;
         this.sample = sample;
         this.amount = amount;
         this.incoming = incoming;
      } else {
         throw new IllegalArgumentException("Неверная квитанция склада");
      }
   }

   public ItemStack sample() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
      return this.sample.clone();
   } }

   public boolean matches(ItemStack item, int count, boolean deposit) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
      return this.amount == count && this.incoming == deposit && this.sample.isSimilar(item);
   } }
}
