package ru.neverland.minttrade.integration;

import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.ApiServices;

public final class WarehouseBridge {
   public WarehouseBridge(JavaPlugin plugin) {
   }

   private ApiServices.Connection api() {
      return ApiServices.connect("NeverLandTownyBuilds", "ru.neverland.townybuilds.api.WarehouseApi", 1, "transfer", "transferred", "acknowledgeMovement");
   }

   public boolean available() {
      return this.api().ready();
   }

   public WarehouseBridge.Result take(UUID town, ItemStack sample, int amount) {
      return this.change("take", town, sample, amount);
   }

   public WarehouseBridge.Result deposit(UUID town, ItemStack sample, int amount) {
      return this.change("deposit", town, sample, amount);
   }

   public WarehouseBridge.Result transfer(UUID operation, UUID town, ItemStack sample, int amount, boolean incoming) throws Exception {
      ApiServices.Connection c = this.api();
      if (!c.ready()) {
         return new WarehouseBridge.Result(WarehouseBridge.Status.UNAVAILABLE, 0);
      } else {
         Map<?, ?> r = (Map<?, ?>)c.invoke(
            "transfer", new Class[]{UUID.class, UUID.class, ItemStack.class, int.class, boolean.class}, operation, town, sample, amount, incoming
         );
         return new WarehouseBridge.Result(WarehouseBridge.Status.valueOf((String)r.get("status")), ((Number)r.get("amount")).intValue());
      }
   }

   public boolean transferred(UUID operation, UUID town) throws Exception {
      ApiServices.Connection c = this.api();
      if (!c.ready()) {
         throw new IllegalStateException("Склад недоступен");
      } else {
         return Boolean.TRUE.equals(c.invoke("transferred", new Class[]{UUID.class, UUID.class}, operation, town));
      }
   }

   public void acknowledge(UUID operation, UUID town) throws Exception {
      ApiServices.Connection c = this.api();
      if (!c.ready()) {
         throw new IllegalStateException("Склад недоступен");
      } else {
         c.invoke("acknowledgeMovement", new Class[]{UUID.class, UUID.class}, operation, town);
      }
   }

   private WarehouseBridge.Result change(String method, UUID town, ItemStack sample, int amount) {
      ApiServices.Connection connection = this.api();
      if (!connection.ready()) {
         return new WarehouseBridge.Result(WarehouseBridge.Status.UNAVAILABLE, 0);
      } else {
         try {
            Map<?, ?> result = (Map<?, ?>)connection.invoke(method, new Class[]{UUID.class, ItemStack.class, int.class}, town, sample, amount);
            return new WarehouseBridge.Result(WarehouseBridge.Status.valueOf((String)result.get("status")), ((Number)result.get("amount")).intValue());
         } catch (RuntimeException | ReflectiveOperationException var7) {
            return new WarehouseBridge.Result(WarehouseBridge.Status.UNAVAILABLE, 0);
         }
      }
   }

   public record Result(WarehouseBridge.Status status, int amount) {
   }

   public static enum Status {
      SUCCESS,
      UNAVAILABLE,
      BUSY,
      INSUFFICIENT,
      FULL;
   }
}
