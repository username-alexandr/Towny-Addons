package ru.neverland.townybuilds.storage;

import com.palmergames.bukkit.towny.TownyAPI;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import ru.neverland.townybuilds.api.WarehouseApi;
import ru.neverland.townybuilds.api.WarehouseMovement;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.data.TownData;

public final class WarehouseService implements WarehouseApi {
   private final DataStore data;
   private final Plugin plugin;

   public WarehouseService(Plugin plugin, DataStore data) {
      this.data = data;this.plugin=plugin;
      Bukkit.getServicesManager().register(WarehouseApi.class, this, plugin, ServicePriority.Normal);
   }

   @Override
   public Map<String, Object> take(UUID town, ItemStack sample, int amount) throws IOException {
      return this.change(null, town, sample, amount, false);
   }

   @Override
   public Map<String, Object> deposit(UUID town, ItemStack sample, int amount) throws IOException {
      return this.change(null, town, sample, amount, true);
   }

   @Override
   public Map<String, Object> transfer(UUID operation, UUID town, ItemStack sample, int amount, boolean incoming) throws IOException {
      return this.change(Objects.requireNonNull(operation), town, sample, amount, incoming);
   }

   @Override
   public boolean transferred(UUID operation, UUID town) {
      if (!Bukkit.isPrimaryThread()) {
         throw new IllegalStateException("Нужен основной поток");
      } else {
         return this.data.town(town).warehouseMovements().containsKey(operation);
      }
   }

   @Override
   public void acknowledgeMovement(UUID operation, UUID town) throws IOException {
      if (!Bukkit.isPrimaryThread()) {
         throw new IllegalStateException("Нужен основной поток");
      } else {
         TownData state = this.data.town(town);
         WarehouseMovement old = state.warehouseMovements().get(operation);
         if (old != null) {
            ru.neverland.core.AuditTrail.require(audit(operation,town,old.sample(),old.amount(),old.incoming()));
            state.removeWarehouseMovement(operation);

            try {
               this.data.saveOrThrow();
            } catch (RuntimeException | IOException var6) {
               state.putWarehouseMovement(old);
               throw var6;
            }
         }
      }
   }

   private boolean audit(UUID operation,UUID town,ItemStack sample,int amount,boolean incoming){var city=ru.neverland.core.AuditTrail.town(town);var unknown=ru.neverland.core.AuditRecord.Party.unknown();return ru.neverland.core.AuditTrail.record(plugin,"warehouse:"+town,operation.toString(),"WAREHOUSE_LEG",incoming?"CREDIT":"DEBIT",unknown,incoming?unknown:city,incoming?city:unknown,ru.neverland.core.AuditTrail.item(sample),amount,"","Warehouse API");}
   private Map<String, Object> result(String status, int amount) {
      return Map.of("status", status, "amount", amount);
   }

   private Map<String, Object> change(UUID operation, UUID town, ItemStack sample, int amount, boolean incoming) throws IOException {
      if (!Bukkit.isPrimaryThread()) {
         throw new IllegalStateException("Склад требует основного потока");
      } else if (sample == null || sample.getType().isAir() || amount < 1 || amount > 1000000) {
         throw new IllegalArgumentException("Неверный груз");
      } else if (!this.data.writable()) {
         return this.result("UNAVAILABLE", 0);
      } else {
         WarehouseMovement prior = operation == null ? null : this.data.town(town).warehouseMovements().get(operation);
         if (prior == null) {
            if (TownyAPI.getInstance().getTown(town) == null) {
               return this.result("UNAVAILABLE", 0);
            } else if (this.data.storageBusy(town, "warehouse")) {
               return this.result("BUSY", 0);
            } else {
               TownData state = this.data.town(town);
               ItemStack[] before = state.storage();
               ItemStack[] work = StockMath.copy(before);
               if (incoming) {
                  ItemStack cargo = sample.clone();
                  cargo.setAmount(amount);
                  if (!StockMath.insert(work, new ItemStack[]{cargo})) {
                     return this.result("FULL", 0);
                  }
               } else {
                  int have = StockMath.count(work, sample);
                  if (have < amount) {
                     return this.result("INSUFFICIENT", have);
                  }

                  StockMath.take(work, sample, amount, 0);
               }

               state.setStorage(work, work.length);
               if (operation != null) {
                  state.putWarehouseMovement(new WarehouseMovement(operation, sample, amount, incoming));
               }

               this.data.markDirty();

               try {
                  this.data.saveOrThrow();
               } catch (RuntimeException | IOException var11) {
                  state.setStorage(before, before.length);
                  if (operation != null) {
                     state.removeWarehouseMovement(operation);
                  }

                  throw var11;
               }

               audit(operation==null?UUID.randomUUID():operation,town,sample,amount,incoming);
               return this.result("SUCCESS", amount);
            }
         } else if (!prior.matches(sample, amount, incoming)) {
            throw new IllegalArgumentException("ID склада занят другими условиями");
         } else {
            audit(operation,town,sample,amount,incoming);return this.result("SUCCESS", amount);
         }
      }
   }
}
