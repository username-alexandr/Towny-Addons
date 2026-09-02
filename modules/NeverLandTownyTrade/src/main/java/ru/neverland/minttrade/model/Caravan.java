package ru.neverland.minttrade.model;

import org.bukkit.inventory.ItemStack;
import ru.neverland.minttrade.util.TradeMath;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Caravan {
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
    private final long departedAt;
    private long arrivesAt;
    private final long incidentAt;
    private final boolean shouldDelay;
    private boolean incidentHandled;
    private CaravanStatus status;

    public Caravan(UUID id, UUID sellerId, UUID buyerId, String exportId, ItemStack cargoItem, int totalCargo, int remainingCargo,
                   double basePrice, double escrow, Map<UUID, Double> tariffs, List<RoutePoint> route,
                   long departedAt, long arrivesAt, long incidentAt, boolean shouldDelay,
                   boolean incidentHandled, CaravanStatus status) {
        this.id = id; this.sellerId = sellerId; this.buyerId = buyerId; this.exportId = exportId; this.cargoItem = cargoItem.clone();
        this.totalCargo = totalCargo; this.remainingCargo = remainingCargo; this.basePrice = basePrice;
        this.escrow = escrow; this.tariffs = new LinkedHashMap<>(tariffs); this.route = new ArrayList<>(route);
        this.departedAt = departedAt; this.arrivesAt = arrivesAt; this.incidentAt = incidentAt;
        this.shouldDelay = shouldDelay; this.incidentHandled = incidentHandled; this.status = status;
    }
    public UUID id() { return id; }
    public String shortId() { return id.toString().substring(0, 8); }
    public UUID sellerId() { return sellerId; }
    public UUID buyerId() { return buyerId; }
    public String exportId() { return exportId; }
    public ItemStack cargoItem() { return cargoItem.clone(); }
    public int totalCargo() { return totalCargo; }
    public int remainingCargo() { return remainingCargo; }
    public void remainingCargo(int value) { remainingCargo = Math.max(0, value); }
    public double basePrice() { return basePrice; }
    public double escrow() { return escrow; }
    public Map<UUID, Double> tariffs() { return Map.copyOf(tariffs); }
    public List<RoutePoint> route() { return List.copyOf(route); }
    public long departedAt() { return departedAt; }
    public long arrivesAt() { return arrivesAt; }
    public void arrivesAt(long value) { arrivesAt = value; }
    public void delay(long millis) { arrivesAt += Math.max(0, millis); }
    public long incidentAt() { return incidentAt; }
    public boolean shouldDelay() { return shouldDelay; }
    public boolean incidentHandled() { return incidentHandled; }
    public void incidentHandled(boolean value) { incidentHandled = value; }
    public CaravanStatus status() { return status; }
    public void status(CaravanStatus value) { status = value; }
    public double progress(long now) {
        return TradeMath.progress(departedAt, arrivesAt, now);
    }
    public int campStops() { return (int) route.stream().filter(point -> point.kind() == RoutePoint.Kind.CAMP).count(); }
}
