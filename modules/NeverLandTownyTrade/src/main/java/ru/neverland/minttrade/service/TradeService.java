package ru.neverland.minttrade.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.minttrade.integration.BuildBridge;
import ru.neverland.minttrade.integration.TownyHook;
import ru.neverland.minttrade.integration.TaxesBridge;
import ru.neverland.minttrade.integration.WarehouseBridge;
import ru.neverland.minttrade.model.Caravan;
import ru.neverland.minttrade.model.CaravanStatus;
import ru.neverland.minttrade.model.ExportDefinition;
import ru.neverland.minttrade.model.TradeHistory;
import ru.neverland.minttrade.model.TradeOffer;
import ru.neverland.minttrade.util.ColorUtil;
import ru.neverland.minttrade.util.TimeUtil;
import ru.neverland.minttrade.util.TradeMath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class TradeService {
    public enum ProposeResult { SUCCESS, SAME_TOWN, MARKET_REQUIRED, ROUTE_LIMIT, DUPLICATE, ROUTE_UNAVAILABLE, SANCTIONED }
    public enum AcceptResult { SUCCESS, NOT_FOUND, NOT_BUYER, MARKET_REQUIRED, ROUTE_LIMIT, ROUTE_UNAVAILABLE,
        NO_MONEY, STOCK_LOW, WAREHOUSE_BUSY, WAREHOUSE_UNAVAILABLE, ECONOMY_ERROR, SAVE_ERROR, SANCTIONED }
    public enum CancelResult { SUCCESS, NOT_FOUND, NOT_PARTY, WAREHOUSE_BUSY, WAREHOUSE_UNAVAILABLE }
    public record ProposeOutcome(ProposeResult result, TradeOffer offer) {}
    public record AcceptOutcome(AcceptResult result, Caravan caravan, double required) {}

    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final BuildBridge builds;
    private final WarehouseBridge warehouse;
    private final ExportRegistry registry;
    private final TradeRepository repository;
    private final RoutePlanner routes;
    private final EconomyService economy;
    private final MessageService messages;
    private final TaxesBridge taxes;
    private BukkitTask task;

    public TradeService(JavaPlugin plugin, TownyHook towny, BuildBridge builds, WarehouseBridge warehouse,
                        ExportRegistry registry, TradeRepository repository, RoutePlanner routes,
                        EconomyService economy, MessageService messages, TaxesBridge taxes) {
        this.plugin = plugin; this.towny = towny; this.builds = builds; this.warehouse = warehouse;
        this.registry = registry; this.repository = repository; this.routes = routes; this.economy = economy; this.messages = messages; this.taxes = taxes;
    }
    public void start() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }
    public void shutdown() { if (task != null) task.cancel(); task = null; repository.save(); }

    public ProposeOutcome propose(Town seller, Town buyer, ExportDefinition definition) {
        if (seller == null || buyer == null || definition == null) return new ProposeOutcome(ProposeResult.ROUTE_UNAVAILABLE, null);
        if (seller.getUUID().equals(buyer.getUUID())) return new ProposeOutcome(ProposeResult.SAME_TOWN, null);
        if (taxes.tradeBlocked(seller.getUUID(), buyer.getUUID())) return new ProposeOutcome(ProposeResult.SANCTIONED, null);
        if (marketLevel(seller) <= 0 || marketLevel(buyer) <= 0) return new ProposeOutcome(ProposeResult.MARKET_REQUIRED, null);
        if (!hasRouteSlot(seller) || !hasRouteSlot(buyer)) return new ProposeOutcome(ProposeResult.ROUTE_LIMIT, null);
        if (routes.plan(seller, buyer) == null) return new ProposeOutcome(ProposeResult.ROUTE_UNAVAILABLE, null);
        boolean duplicate = repository.offers().stream().anyMatch(value -> value.sellerId().equals(seller.getUUID())
                && value.buyerId().equals(buyer.getUUID()) && value.exportId().equalsIgnoreCase(definition.id()));
        if (duplicate) return new ProposeOutcome(ProposeResult.DUPLICATE, null);
        long now = System.currentTimeMillis();
        TradeOffer offer = new TradeOffer(UUID.randomUUID(), seller.getUUID(), buyer.getUUID(), definition.id(), now,
                now + Math.max(1, plugin.getConfig().getLong("trade.proposal-expiration-hours", 24)) * 3_600_000L);
        repository.add(offer); saveNow();
        if (plugin.getConfig().getBoolean("announcements.proposal", true)) announce(buyer, "proposal-received", Map.of(
                "town", seller.getName(), "export", ColorUtil.strip(definition.name())));
        return new ProposeOutcome(ProposeResult.SUCCESS, offer);
    }

    public AcceptOutcome accept(Town buyer, TradeOffer offer) {
        if (offer == null) return new AcceptOutcome(AcceptResult.NOT_FOUND, null, 0);
        if (buyer == null || !buyer.getUUID().equals(offer.buyerId())) return new AcceptOutcome(AcceptResult.NOT_BUYER, null, 0);
        Town seller = towny.town(offer.sellerId()); ExportDefinition definition = registry.get(offer.exportId());
        if (seller == null || definition == null) { repository.remove(offer); saveNow(); return new AcceptOutcome(AcceptResult.NOT_FOUND, null, 0); }
        if (taxes.tradeBlocked(seller.getUUID(), buyer.getUUID())) return new AcceptOutcome(AcceptResult.SANCTIONED, null, 0);
        if (marketLevel(seller) <= 0 || marketLevel(buyer) <= 0) return new AcceptOutcome(AcceptResult.MARKET_REQUIRED, null, 0);
        if (!hasRouteSlot(seller) || !hasRouteSlot(buyer)) return new AcceptOutcome(AcceptResult.ROUTE_LIMIT, null, 0);
        RoutePlanner.RoutePlan plan = routes.plan(seller, buyer);
        if (plan == null) return new AcceptOutcome(AcceptResult.ROUTE_UNAVAILABLE, null, 0);
        Map<UUID, Double> tolls = tolls(definition, plan.transitTowns(), seller, buyer);
        double total = cents(definition.price() + tolls.values().stream().mapToDouble(Double::doubleValue).sum());
        if (!economy.canWithdraw(buyer, total)) return new AcceptOutcome(AcceptResult.NO_MONEY, null, total);
        WarehouseBridge.Result taken = warehouse.take(seller.getUUID(), definition.item(), definition.amount());
        if (taken.status() == WarehouseBridge.Status.INSUFFICIENT) return new AcceptOutcome(AcceptResult.STOCK_LOW, null, total);
        if (taken.status() == WarehouseBridge.Status.BUSY) return new AcceptOutcome(AcceptResult.WAREHOUSE_BUSY, null, total);
        if (taken.status() != WarehouseBridge.Status.SUCCESS) return new AcceptOutcome(AcceptResult.WAREHOUSE_UNAVAILABLE, null, total);
        if (!economy.withdraw(buyer, total, definition)) {
            warehouse.deposit(seller.getUUID(), definition.item(), definition.amount());
            return new AcceptOutcome(AcceptResult.ECONOMY_ERROR, null, total);
        }
        long now = System.currentTimeMillis(), arrives = now + plan.durationMillis();
        Caravan caravan = new Caravan(UUID.randomUUID(), seller.getUUID(), buyer.getUUID(), definition.id(), definition.item(),
                definition.amount(), definition.amount(), definition.price(), total, tolls, plan.points(), now, arrives,
                now + Math.max(1000, Math.round(plan.durationMillis() * 0.55)),
                ThreadLocalRandom.current().nextDouble() < plan.delayChance(), false, CaravanStatus.ACTIVE);
        repository.remove(offer); repository.add(caravan);
        if (!repository.save()) {
            repository.remove(caravan); repository.add(offer);
            if (!economy.deposit(buyer, total, definition, "economy.refund-reason")) repository.addPending(buyer.getUUID(), total);
            WarehouseBridge.Result restored = warehouse.deposit(seller.getUUID(), definition.item(), definition.amount());
            if (restored.status() != WarehouseBridge.Status.SUCCESS)
                plugin.getLogger().severe("Не удалось откатить товар после ошибки сохранения сделки " + caravan.id());
            repository.save();
            return new AcceptOutcome(AcceptResult.SAVE_ERROR, null, total);
        }
        if (plugin.getConfig().getBoolean("announcements.departure", true)) announceBoth(caravan, "caravan-town-departed", definition, Map.of());
        return new AcceptOutcome(AcceptResult.SUCCESS, caravan, total);
    }

    public CancelResult reject(Town buyer, TradeOffer offer) {
        if (offer == null) return CancelResult.NOT_FOUND;
        if (buyer == null || !offer.buyerId().equals(buyer.getUUID())) return CancelResult.NOT_PARTY;
        repository.remove(offer); saveNow(); return CancelResult.SUCCESS;
    }
    public CancelResult cancelOffer(Town seller, TradeOffer offer) {
        if (offer == null) return CancelResult.NOT_FOUND;
        if (seller == null || !offer.sellerId().equals(seller.getUUID())) return CancelResult.NOT_PARTY;
        repository.remove(offer); saveNow(); return CancelResult.SUCCESS;
    }
    public CancelResult cancelCaravan(Caravan caravan) {
        if (caravan == null) return CancelResult.NOT_FOUND;
        ExportDefinition definition = definition(caravan);
        WarehouseBridge.Result restored = warehouse.deposit(caravan.sellerId(), caravan.cargoItem(), caravan.remainingCargo());
        if (restored.status() == WarehouseBridge.Status.BUSY) return CancelResult.WAREHOUSE_BUSY;
        if (restored.status() != WarehouseBridge.Status.SUCCESS) return CancelResult.WAREHOUSE_UNAVAILABLE;
        Town buyer = towny.town(caravan.buyerId());
        if (!economy.deposit(buyer, caravan.escrow(), definition, "economy.refund-reason")) repository.addPending(caravan.buyerId(), caravan.escrow());
        finishHistory(caravan, CaravanStatus.CANCELLED);
        return CancelResult.SUCCESS;
    }
    public boolean forceComplete(Caravan caravan) { return caravan != null && deliver(caravan); }

    public double tariff(Town town) { return repository.tariff(town.getUUID(), plugin.getConfig().getDouble("tariffs.default-percent", 0)); }
    public double maxTariff() { return plugin.getConfig().getDouble("tariffs.maximum-percent", 20); }
    public boolean setTariff(Town town, double percent) {
        double max = maxTariff();
        if (town == null || percent < 0 || percent > max) return false;
        repository.setTariff(town.getUUID(), cents(percent)); saveNow(); return true;
    }
    public int marketLevel(Town town) { return town == null ? 0 : builds.marketLevel(town.getUUID()); }
    public int routeLimit(Town town) { return plugin.getConfig().getInt("market.routes-by-level." + marketLevel(town), 0); }
    public int activeCount(Town town) { return town == null ? 0 : repository.caravans(town.getUUID()).size(); }
    public boolean hasRouteSlot(Town town) { return activeCount(town) < routeLimit(town); }

    private void tick() {
        long now = System.currentTimeMillis();
        for (TradeOffer offer : new ArrayList<>(repository.offers())) if (now >= offer.expiresAt()) repository.remove(offer);
        for (Caravan caravan : new ArrayList<>(repository.caravans())) {
            if (towny.town(caravan.buyerId()) == null) { cancelCaravan(caravan); continue; }
            if (!caravan.incidentHandled() && now >= caravan.incidentAt()) {
                caravan.incidentHandled(true);
                if (caravan.shouldDelay()) {
                    long delay = Math.max(1, plugin.getConfig().getLong("routes.delay.seconds", 300)) * 1000;
                    caravan.delay(delay);
                    if (plugin.getConfig().getBoolean("announcements.delay", true)) announceBoth(caravan, "caravan-delayed", definition(caravan),
                            Map.of("time", TimeUtil.format(delay)));
                }
                repository.changed();
            }
            if (now >= caravan.arrivesAt() || caravan.status() == CaravanStatus.WAITING_WAREHOUSE) deliver(caravan);
        }
        retryCredits(); repository.saveIfDirty();
    }
    private boolean deliver(Caravan caravan) {
        ExportDefinition definition = definition(caravan);
        WarehouseBridge.Result delivered = warehouse.deposit(caravan.buyerId(), caravan.cargoItem(), caravan.remainingCargo());
        if (delivered.status() != WarehouseBridge.Status.SUCCESS) {
            if (caravan.status() != CaravanStatus.WAITING_WAREHOUSE) {
                caravan.status(CaravanStatus.WAITING_WAREHOUSE); repository.changed();
                Town buyer = towny.town(caravan.buyerId()); if (buyer != null) announce(buyer, "waiting-warehouse", Map.of());
            }
            return false;
        }
        caravan.remainingCargo(0); Town seller = towny.town(caravan.sellerId()), buyer = towny.town(caravan.buyerId());
        if (!economy.deposit(seller, caravan.basePrice(), definition, "economy.sale-reason")) repository.addPending(caravan.sellerId(), caravan.basePrice());
        for (Map.Entry<UUID, Double> toll : caravan.tariffs().entrySet()) {
            Town transit = towny.town(toll.getKey());
            if (!economy.deposit(transit, toll.getValue(), definition, "economy.tariff-reason")) repository.addPending(toll.getKey(), toll.getValue());
        }
        finishHistory(caravan, CaravanStatus.COMPLETED);
        if (plugin.getConfig().getBoolean("announcements.arrival", true)) {
            if (buyer != null) announce(buyer, "caravan-arrived", Map.of("from", seller == null ? "Удалённый город" : seller.getName()));
            if (seller != null) announce(seller, "caravan-completed", Map.of("export", ColorUtil.strip(definition.name()), "amount", economy.format(caravan.basePrice())));
        }
        return true;
    }
    private void finishHistory(Caravan caravan, CaravanStatus status) {
        repository.remove(caravan);
        repository.addHistory(new TradeHistory(caravan.id(), caravan.sellerId(), caravan.buyerId(), caravan.exportId(),
                caravan.totalCargo(), caravan.basePrice(), caravan.tariffs().values().stream().mapToDouble(Double::doubleValue).sum(),
                System.currentTimeMillis(), status), plugin.getConfig().getInt("trade.history-limit-per-town", 30));
        saveNow();
    }
    private Map<UUID, Double> tolls(ExportDefinition definition, List<UUID> towns, Town seller, Town buyer) {
        Map<UUID, Double> result = new LinkedHashMap<>();
        double preference = taxes.preferenceMultiplier(seller.getUUID(), buyer.getUUID());
        for (UUID townId : towns) {
            double percent = repository.tariff(townId, plugin.getConfig().getDouble("tariffs.default-percent", 0));
            double amount = cents(TradeMath.tariff(definition.price(), percent) * preference); if (amount > 0) result.put(townId, amount);
        }
        return result;
    }
    private void retryCredits() {
        ExportDefinition fallback = registry.all().stream().findFirst().orElse(null); if (fallback == null) return;
        for (Map.Entry<UUID, Double> entry : repository.pendingCredits().entrySet()) {
            Town town = towny.town(entry.getKey());
            if (economy.deposit(town, entry.getValue(), fallback, "economy.refund-reason")) repository.clearPending(entry.getKey());
        }
    }
    private ExportDefinition definition(Caravan caravan) {
        ExportDefinition current = registry.get(caravan.exportId());
        return current != null ? current : new ExportDefinition(caravan.exportId(), "&f" + caravan.exportId(),
                caravan.cargoItem().getType() == Material.AIR ? Material.CHEST : caravan.cargoItem().getType(), -1,
                List.of(), caravan.cargoItem().getType().name(), caravan.cargoItem(), caravan.totalCargo(), caravan.basePrice());
    }
    private void announceBoth(Caravan caravan, String key, ExportDefinition definition, Map<String, ?> extra) {
        Town seller = towny.town(caravan.sellerId()), buyer = towny.town(caravan.buyerId());
        Map<String, Object> values = new LinkedHashMap<>(); values.put("from", seller == null ? "?" : seller.getName());
        values.put("to", buyer == null ? "?" : buyer.getName()); values.put("export", ColorUtil.strip(definition.name())); values.putAll(extra);
        if (seller != null) announce(seller, key, values); if (buyer != null) announce(buyer, key, values);
    }
    private void announce(Town town, String key, Map<String, ?> values) {
        String text = messages.text(key, values, true);
        for (Player player : Bukkit.getOnlinePlayers()) { Town playerTown = towny.town(player); if (town.equals(playerTown)) player.sendMessage(text); }
    }
    private void saveNow() { if (plugin.getConfig().getBoolean("trade.save-immediately", true)) repository.save(); else repository.changed(); }
    private double cents(double value) { return TradeMath.cents(value); }

    public ExportRegistry registry() { return registry; }
    public TradeRepository repository() { return repository; }
    public EconomyService economy() { return economy; }
    public TradeOffer offer(String id) { return repository.findOffer(id); }
    public Caravan caravan(String id) { return repository.findCaravan(id); }
    public List<TradeOffer> incoming(Town town) { return town == null ? List.of() : repository.incoming(town.getUUID()); }
    public List<TradeOffer> outgoing(Town town) { return town == null ? List.of() : repository.outgoing(town.getUUID()); }
    public List<Caravan> caravans(Town town) { return town == null ? List.of() : repository.caravans(town.getUUID()); }
    public List<TradeHistory> history(Town town) { return town == null ? List.of() : repository.history(town.getUUID()); }
    public ExportDefinition definition(TradeOffer offer) { return offer == null ? null : registry.get(offer.exportId()); }
    public ExportDefinition definitionOf(Caravan caravan) { return caravan == null ? null : definition(caravan); }
}
