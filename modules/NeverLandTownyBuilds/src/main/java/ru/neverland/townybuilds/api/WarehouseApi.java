package ru.neverland.townybuilds.api;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import ru.neverland.core.ApiContract;

public interface WarehouseApi extends ApiContract {
   @Override
   default Set<String> capabilities() {
      return Set.of("take", "deposit", "transfer", "transferred", "acknowledgeMovement");
   }

   Map<String, Object> take(UUID var1, ItemStack var2, int var3) throws IOException;

   Map<String, Object> deposit(UUID var1, ItemStack var2, int var3) throws IOException;

   Map<String, Object> transfer(UUID var1, UUID var2, ItemStack var3, int var4, boolean var5) throws IOException;

   boolean transferred(UUID var1, UUID var2);

   void acknowledgeMovement(UUID var1, UUID var2) throws IOException;
}
