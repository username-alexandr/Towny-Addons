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

   public String settlement() {
      return this.settlement;
   }

   public void settlement(String value) {
      if (!Set.of("PREPARED", "ACTIVE", "DELIVERING", "PAYING", "RETURNING", "COMPLETE", "CANCELLED", "LEGACY_REVIEW").contains(value)) {
         throw new IllegalArgumentException("Неизвестный этап каравана");
      } else {
         this.settlement = value;
      }
   }

   public boolean legacyFunded() {
      return this.legacyFunded;
   }

   public void legacyFunded(boolean value) {
      this.legacyFunded = value;
   }

   public boolean terminal() {
      return this.settlement.equals("COMPLETE") || this.settlement.equals("CANCELLED");
   }

   public UUID operation(String step) {
      return EffectJournal.id("caravan:" + this.id + ":" + step);
   }

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

   public UUID id() {
      return this.id;
   }

   public String shortId() {
      return this.id.toString().substring(0, 8);
   }

   public UUID sellerId() {
      return this.sellerId;
   }

   public UUID buyerId() {
      return this.buyerId;
   }

   public String exportId() {
      return this.exportId;
   }

   public ItemStack cargoItem() {
      return this.cargoItem.clone();
   }

   public int totalCargo() {
      return this.totalCargo;
   }

   public int remainingCargo() {
      return this.remainingCargo;
   }

   public void remainingCargo(int value) {
      this.remainingCargo = Math.max(0, value);
   }

   public double basePrice() {
      return this.basePrice;
   }

   public double escrow() {
      return this.escrow;
   }

   public Map<UUID, Double> tariffs() {
      return Map.copyOf(this.tariffs);
   }

   public List<RoutePoint> route() {
      return List.copyOf(this.route);
   }

   public long departedAt() {
      return this.departedAt;
   }

   public long arrivesAt() {
      return this.arrivesAt;
   }

   public void arrivesAt(long value) {
      this.arrivesAt = value;
   }

   public void delay(long millis) {
      this.arrivesAt = this.arrivesAt + Math.max(0L, millis);
   }

   public long incidentAt() {
      return this.incidentAt;
   }

   public boolean shouldDelay() {
      return this.shouldDelay;
   }

   public boolean incidentHandled() {
      return this.incidentHandled;
   }

   public void incidentHandled(boolean value) {
      this.incidentHandled = value;
   }

   public CaravanStatus status() {
      return this.status;
   }

   public void status(CaravanStatus value) {
      this.status = value;
   }

   public double progress(long now) {
      return TradeMath.progress(this.departedAt, this.arrivesAt, timer().now(now));
   }

   public int campStops() {
      return (int)this.route.stream().filter(point -> point.kind() == Kind.CAMP).count();
   }
   public ru.neverland.core.ActivityTimer timer(){return new ru.neverland.core.ActivityTimer(arrivesAt,adminPausedAt,adminDuration);}
   public void timer(ru.neverland.core.ActivityTimer value){arrivesAt=value.deadline();adminPausedAt=value.pausedAt();adminDuration=value.duration();}
   public void travelTimes(long departed,long incident){departedAt=departed;incidentAt=incident;}
   public void editTimer(String action,long minutes,long now){
      var before=timer();var after=before.edit(action,minutes,now);
      if(action.equals("resume")){long delta=now-before.pausedAt();departedAt=Math.addExact(departedAt,delta);incidentAt=Math.addExact(incidentAt,delta);}
      // Restart only the travel timer; cargo, payment IDs and the incident receipt remain intact.
      if(action.equals("restart"))departedAt=after.now(now);
      timer(after);
   }
}
