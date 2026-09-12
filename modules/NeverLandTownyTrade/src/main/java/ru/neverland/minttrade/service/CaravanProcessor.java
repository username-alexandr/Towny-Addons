package ru.neverland.minttrade.service;

import java.util.UUID;
import java.util.Map.Entry;
import ru.neverland.minttrade.model.Caravan;
import ru.neverland.minttrade.model.CaravanStatus;

public final class CaravanProcessor {
   private CaravanProcessor() {
   }

   public static void advance(Caravan c, CaravanProcessor.Store store, CaravanProcessor.Gateway gateway) throws Exception {
      String var3 = c.settlement();
      switch (var3) {
         case "PREPARED":
            if (!gateway.reserve(c)) {
               return;
            }

            c.settlement(gateway.debit(c) ? "ACTIVE" : "RETURNING");
            store.save();
            break;
         case "DELIVERING":
            if (!gateway.deliver(c)) {
               return;
            }

            c.settlement("PAYING");
            store.save();
            break;
         case "PAYING":
            if (!gateway.credit(c, c.sellerId(), c.basePrice(), "seller")) {
               return;
            }

            for (Entry<UUID, Double> toll : c.tariffs().entrySet()) {
               if (!gateway.credit(c, toll.getKey(), toll.getValue(), "tariff")) {
                  return;
               }
            }

            store.finish(c, CaravanStatus.COMPLETED);
            break;
         case "RETURNING":
            if (gateway.sourceTaken(c) && !gateway.returnStock(c)) {
               return;
            }

            if (gateway.funded(c) && !gateway.credit(c, c.buyerId(), c.escrow(), "refund")) {
               return;
            }

            store.finish(c, CaravanStatus.CANCELLED);
      }
   }

   public interface Gateway {
      boolean reserve(Caravan var1) throws Exception;

      boolean debit(Caravan var1) throws Exception;

      boolean deliver(Caravan var1) throws Exception;

      boolean credit(Caravan var1, UUID var2, double var3, String var5) throws Exception;

      boolean sourceTaken(Caravan var1) throws Exception;

      boolean funded(Caravan var1);

      boolean returnStock(Caravan var1) throws Exception;
   }

   public interface Store {
      void save();

      void finish(Caravan var1, CaravanStatus var2);
   }
}
