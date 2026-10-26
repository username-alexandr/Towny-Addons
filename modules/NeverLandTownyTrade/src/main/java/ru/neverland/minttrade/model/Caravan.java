package ru.neverland.minttrade.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import ru.neverland.core.EffectJournal;
import ru.neverland.minttrade.model.RoutePoint.Kind;
import ru.neverland.minttrade.util.TradeMath;

public final class Caravan {
   private String settlement = "PREPARED";
   private boolean legacyFunded;
   private final UUID id;
   private final UUID sellerId;
   private final UUID buyerId;
   private final String exportId;
   private final ItemStack cargoItem;
   private final int totalCargo;
   private int remainingCargo;
   private final double basePrice;
   private final double escrow;
   private final Map<UUID, Double> tariffs;
   private final List<RoutePoint> route;
   private long departedAt;
   private long arrivesAt;
   private long incidentAt;
   private long adminPausedAt, adminDuration;
   private final boolean shouldDelay;
   private boolean incidentHandled;
   private CaravanStatus status;

   public String settlement() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
      return this.settlement;
   } }

   public void settlement(String value) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
      if (!Set.of("PREPARED", "ACTIVE", "DELIVERING", "PAYING", "RETURNING", "COMPLETE", "CANCELLED", "LEGACY_REVIEW").contains(value)) {
         throw new IllegalArgumentException("Неизвестный этап каравана");
      } else {
         this.settlement = value;
      }
   } }

   public boolean legacyFunded() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
      return this.legacyFunded;
   } }

   public void legacyFunded(boolean value) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
      this.legacyFunded = value;
   } }

   public boolean terminal() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
      return this.settlement.equals("COMPLETE") || this.settlement.equals("CANCELLED");
   } }

   public UUID operation(String step) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
      return EffectJournal.id("caravan:" + this.id + ":" + step);
   } }

   public Caravan(
      UUID id,
      UUID sellerId,
      UUID buyerId,
      String exportId,
      ItemStack cargoItem,
      int totalCargo,
      int remainingCargo,
      double basePrice,
      double escrow,
      Map<UUID, Double> tariffs,
      List<RoutePoint> route,
      long departedAt,
      long arrivesAt,
      long incidentAt,
      boolean shouldDelay,
      boolean incidentHandled,
      CaravanStatus status
   ) {
      this.id = id;
      this.sellerId = sellerId;
      this.buyerId = buyerId;
      this.exportId = exportId;
      this.cargoItem = cargoItem.clone();
      this.totalCargo = totalCargo;
      this.remainingCargo = remainingCargo;
      this.basePrice = basePrice;
      this.escrow = escrow;
      this.tariffs = new LinkedHashMap<>(tariffs);
      this.route = new ArrayList<>(route);
      this.departedAt = departedAt;
      this.arrivesAt = arrivesAt;
      this.incidentAt = incidentAt; this.adminDuration=Math.max(1,arrivesAt-departedAt);
      this.shouldDelay = shouldDelay;
      this.incidentHandled = incidentHandled;
      this.status = status;
   }

   public UUID id() { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
      return this.id;
   } }

   public String shortId() { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
      return this.id.toString().substring(0, 8);
   } }

   public UUID sellerId() { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {
      return this.sellerId;
   } }

   public UUID buyerId() { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {
      return this.buyerId;
   } }

   public String exportId() { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {
      return this.exportId;
   } }

   public ItemStack cargoItem() { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {
      return this.cargoItem.clone();
   } }

   public int totalCargo() { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {
      return this.totalCargo;
   } }

   public int remainingCargo() { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {
      return this.remainingCargo;
   } }

   public void remainingCargo(int value) { try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {
      this.remainingCargo = Math.max(0, value);
   } }

   public double basePrice() { try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {
      return this.basePrice;
   } }

   public double escrow() { try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {
      return this.escrow;
   } }

   public Map<UUID, Double> tariffs() { try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {
      return Map.copyOf(this.tariffs);
   } }

   public List<RoutePoint> route() { try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) {
      return List.copyOf(this.route);
   } }

   public long departedAt() { try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) {
      return this.departedAt;
   } }

   public long arrivesAt() { try (var suiteGuard21 = ru.neverland.core.SuiteState.guard()) {
      return this.arrivesAt;
   } }

   public void arrivesAt(long value) { try (var suiteGuard22 = ru.neverland.core.SuiteState.guard()) {
      this.arrivesAt = value;
   } }

   public void delay(long millis) { try (var suiteGuard23 = ru.neverland.core.SuiteState.guard()) {
      this.arrivesAt = this.arrivesAt + Math.max(0L, millis);
   } }

   public long incidentAt() { try (var suiteGuard24 = ru.neverland.core.SuiteState.guard()) {
      return this.incidentAt;
   } }

   public boolean shouldDelay() { try (var suiteGuard25 = ru.neverland.core.SuiteState.guard()) {
      return this.shouldDelay;
   } }

   public boolean incidentHandled() { try (var suiteGuard26 = ru.neverland.core.SuiteState.guard()) {
      return this.incidentHandled;
   } }

   public void incidentHandled(boolean value) { try (var suiteGuard27 = ru.neverland.core.SuiteState.guard()) {
      this.incidentHandled = value;
   } }

   public CaravanStatus status() { try (var suiteGuard28 = ru.neverland.core.SuiteState.guard()) {
      return this.status;
   } }

   public void status(CaravanStatus value) { try (var suiteGuard29 = ru.neverland.core.SuiteState.guard()) {
      this.status = value;
   } }

   public double progress(long now) { try (var suiteGuard30 = ru.neverland.core.SuiteState.guard()) {
      return TradeMath.progress(this.departedAt, this.arrivesAt, timer().now(now));
   } }

   public int campStops() { try (var suiteGuard31 = ru.neverland.core.SuiteState.guard()) {
      return (int)this.route.stream().filter(point -> point.kind() == Kind.CAMP).count();
   } }
   public ru.neverland.core.ActivityTimer timer(){ try (var suiteGuard32 = ru.neverland.core.SuiteState.guard()) {return new ru.neverland.core.ActivityTimer(arrivesAt,adminPausedAt,adminDuration);} }
   public void timer(ru.neverland.core.ActivityTimer value){ try (var suiteGuard33 = ru.neverland.core.SuiteState.guard()) {arrivesAt=value.deadline();adminPausedAt=value.pausedAt();adminDuration=value.duration();} }
   public void travelTimes(long departed,long incident){ try (var suiteGuard34 = ru.neverland.core.SuiteState.guard()) {departedAt=departed;incidentAt=incident;} }
   public void editTimer(String action,long minutes,long now){ try (var suiteGuard35 = ru.neverland.core.SuiteState.guard()) {
      var before=timer();var after=before.edit(action,minutes,now);
      if(action.equals("resume")){long delta=now-before.pausedAt();departedAt=Math.addExact(departedAt,delta);incidentAt=Math.addExact(incidentAt,delta);}
      // Restart only the travel timer; cargo, payment IDs and the incident receipt remain intact.
      if(action.equals("restart"))departedAt=after.now(now);
      timer(after);
   } }
}
