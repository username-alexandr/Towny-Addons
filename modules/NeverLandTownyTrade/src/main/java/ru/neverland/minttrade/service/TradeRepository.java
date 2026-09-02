package ru.neverland.minttrade.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.minttrade.model.Caravan;
import ru.neverland.minttrade.model.CaravanStatus;
import ru.neverland.minttrade.model.RoutePoint;
import ru.neverland.minttrade.model.TradeHistory;
import ru.neverland.minttrade.model.TradeOffer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TradeRepository {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, TradeOffer> offers = new LinkedHashMap<>();
    private final Map<UUID, Caravan> caravans = new LinkedHashMap<>();
    private final Map<UUID, Double> tariffs = new LinkedHashMap<>();
    private final List<TradeHistory> history = new ArrayList<>();
    private final Map<UUID, Double> pendingCredits = new LinkedHashMap<>();
    private boolean dirty;
    public TradeRepository(JavaPlugin plugin) { this.plugin = plugin; this.file = new File(plugin.getDataFolder(), "trade-data.yml"); }
    public void load() {
        offers.clear(); caravans.clear(); tariffs.clear(); history.clear(); pendingCredits.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        loadOffers(yaml.getConfigurationSection("offers"));
        loadCaravans(yaml.getConfigurationSection("caravans"));
        ConfigurationSection tariffRoot = yaml.getConfigurationSection("tariffs");
        if (tariffRoot != null) for (String id : tariffRoot.getKeys(false)) tariffs.put(UUID.fromString(id), tariffRoot.getDouble(id));
        for (Map<?, ?> raw : yaml.getMapList("history")) try { history.add(history(raw)); } catch (RuntimeException exception) { warn("история", exception); }
        ConfigurationSection pending = yaml.getConfigurationSection("pending-town-credits");
        if (pending != null) for (String id : pending.getKeys(false)) pendingCredits.put(UUID.fromString(id), pending.getDouble(id));
        dirty = false;
    }
    private void loadOffers(ConfigurationSection root) {
        if (root == null) return;
        for (String raw : root.getKeys(false)) try {
            String path = raw + "."; UUID id = UUID.fromString(raw);
            offers.put(id, new TradeOffer(id, UUID.fromString(root.getString(path + "seller")),
                    UUID.fromString(root.getString(path + "buyer")), root.getString(path + "export"),
                    root.getLong(path + "created-at"), root.getLong(path + "expires-at")));
        } catch (RuntimeException exception) { warn("предложение " + raw, exception); }
    }
    private void loadCaravans(ConfigurationSection root) {
        if (root == null) return;
        for (String raw : root.getKeys(false)) try {
            String path = raw + "."; UUID id = UUID.fromString(raw);
            Map<UUID, Double> tolls = new LinkedHashMap<>();
            ConfigurationSection tollRoot = root.getConfigurationSection(path + "tariffs");
            if (tollRoot != null) for (String town : tollRoot.getKeys(false)) tolls.put(UUID.fromString(town), tollRoot.getDouble(town));
            List<RoutePoint> points = new ArrayList<>();
            for (Map<?, ?> map : root.getMapList(path + "route")) points.add(point(map));
            caravans.put(id, new Caravan(id, UUID.fromString(root.getString(path + "seller")), UUID.fromString(root.getString(path + "buyer")),
                    root.getString(path + "export"), root.getItemStack(path + "cargo-item"), root.getInt(path + "total-cargo"), root.getInt(path + "remaining-cargo"),
                    root.getDouble(path + "base-price"), root.getDouble(path + "escrow"), tolls, points,
                    root.getLong(path + "departed-at"), root.getLong(path + "arrives-at"), root.getLong(path + "incident-at"),
                    root.getBoolean(path + "should-delay"), root.getBoolean(path + "incident-handled"),
                    CaravanStatus.valueOf(root.getString(path + "status", "ACTIVE"))));
        } catch (RuntimeException exception) { warn("караван " + raw, exception); }
    }
    public Collection<TradeOffer> offers() { return Collections.unmodifiableCollection(offers.values()); }
    public Collection<Caravan> caravans() { return Collections.unmodifiableCollection(caravans.values()); }
    public List<TradeOffer> incoming(UUID townId) { return offers.values().stream().filter(value -> value.buyerId().equals(townId)).toList(); }
    public List<TradeOffer> outgoing(UUID townId) { return offers.values().stream().filter(value -> value.sellerId().equals(townId)).toList(); }
    public List<Caravan> caravans(UUID townId) { return caravans.values().stream().filter(value -> value.sellerId().equals(townId) || value.buyerId().equals(townId)).toList(); }
    public List<TradeHistory> history(UUID townId) { return history.stream().filter(value -> value.sellerId().equals(townId) || value.buyerId().equals(townId)).toList(); }
    public TradeOffer findOffer(String id) { return find(offers, id); }
    public Caravan findCaravan(String id) { return find(caravans, id); }
    private <T> T find(Map<UUID, T> values, String raw) {
        if (raw == null) return null; String id = raw.toLowerCase(Locale.ROOT); T found = null;
        for (Map.Entry<UUID, T> entry : values.entrySet()) if (entry.getKey().toString().startsWith(id)) {
            if (found != null) return null; found = entry.getValue();
        }
        return found;
    }
    public void add(TradeOffer value) { offers.put(value.id(), value); dirty = true; }
    public void remove(TradeOffer value) { if (value != null) { offers.remove(value.id()); dirty = true; } }
    public void add(Caravan value) { caravans.put(value.id(), value); dirty = true; }
    public void remove(Caravan value) { if (value != null) { caravans.remove(value.id()); dirty = true; } }
    public void changed() { dirty = true; }
    public double tariff(UUID townId, double fallback) { return tariffs.getOrDefault(townId, fallback); }
    public void setTariff(UUID townId, double value) { tariffs.put(townId, value); dirty = true; }
    public void addHistory(TradeHistory entry, int limit) {
        history.add(0, entry);
        int perTown = Math.max(1, limit);
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        history.removeIf(value -> {
            int seller = counts.merge(value.sellerId(), 1, Integer::sum);
            int buyer = counts.merge(value.buyerId(), 1, Integer::sum);
            return seller > perTown && buyer > perTown;
        });
        dirty = true;
    }
    public Map<UUID, Double> pendingCredits() { return Map.copyOf(pendingCredits); }
    public void addPending(UUID townId, double amount) { if (amount > 0) { pendingCredits.merge(townId, amount, Double::sum); dirty = true; } }
    public void clearPending(UUID townId) { pendingCredits.remove(townId); dirty = true; }
    public boolean saveIfDirty() { return !dirty || save(); }
    public boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (TradeOffer value : offers.values()) {
            String path = "offers." + value.id() + ".";
            yaml.set(path + "seller", value.sellerId().toString()); yaml.set(path + "buyer", value.buyerId().toString());
            yaml.set(path + "export", value.exportId()); yaml.set(path + "created-at", value.createdAt()); yaml.set(path + "expires-at", value.expiresAt());
        }
        for (Caravan value : caravans.values()) {
            String path = "caravans." + value.id() + ".";
            yaml.set(path + "seller", value.sellerId().toString()); yaml.set(path + "buyer", value.buyerId().toString());
            yaml.set(path + "export", value.exportId()); yaml.set(path + "cargo-item", value.cargoItem());
            yaml.set(path + "total-cargo", value.totalCargo()); yaml.set(path + "remaining-cargo", value.remainingCargo());
            yaml.set(path + "base-price", value.basePrice()); yaml.set(path + "escrow", value.escrow());
            value.tariffs().forEach((town, amount) -> yaml.set(path + "tariffs." + town, amount));
            yaml.set(path + "route", value.route().stream().map(this::map).toList());
            yaml.set(path + "departed-at", value.departedAt()); yaml.set(path + "arrives-at", value.arrivesAt());
            yaml.set(path + "incident-at", value.incidentAt()); yaml.set(path + "should-delay", value.shouldDelay());
            yaml.set(path + "incident-handled", value.incidentHandled()); yaml.set(path + "status", value.status().name());
        }
        tariffs.forEach((town, percent) -> yaml.set("tariffs." + town, percent));
        yaml.set("history", history.stream().map(this::map).toList());
        pendingCredits.forEach((town, amount) -> yaml.set("pending-town-credits." + town, amount));
        try { yaml.save(file); dirty = false; return true; }
        catch (IOException exception) { plugin.getLogger().severe("Не удалось сохранить trade-data.yml: " + exception.getMessage()); return false; }
    }
    private Map<String, Object> map(RoutePoint point) {
        Map<String, Object> map = new LinkedHashMap<>(); map.put("world-id", point.worldId().toString()); map.put("world-name", point.worldName());
        map.put("x", point.x()); map.put("y", point.y()); map.put("z", point.z()); map.put("kind", point.kind().name());
        map.put("label", point.label()); map.put("level", point.level()); if (point.ownerId() != null) map.put("owner", point.ownerId().toString()); return map;
    }
    private RoutePoint point(Map<?, ?> map) {
        Object owner = map.get("owner");
        return new RoutePoint(UUID.fromString(String.valueOf(map.get("world-id"))), String.valueOf(map.get("world-name")),
                number(map.get("x")), number(map.get("y")), number(map.get("z")), RoutePoint.Kind.valueOf(String.valueOf(map.get("kind"))),
                String.valueOf(map.get("label")), integer(map.get("level")), owner == null ? null : UUID.fromString(String.valueOf(owner)));
    }
    private Map<String, Object> map(TradeHistory value) {
        Map<String, Object> map = new LinkedHashMap<>(); map.put("caravan", value.caravanId().toString()); map.put("seller", value.sellerId().toString());
        map.put("buyer", value.buyerId().toString()); map.put("export", value.exportId()); map.put("amount", value.amount());
        map.put("price", value.price()); map.put("tariffs", value.tariffs()); map.put("completed-at", value.completedAt()); map.put("status", value.status().name()); return map;
    }
    private TradeHistory history(Map<?, ?> map) {
        return new TradeHistory(UUID.fromString(String.valueOf(map.get("caravan"))), UUID.fromString(String.valueOf(map.get("seller"))),
                UUID.fromString(String.valueOf(map.get("buyer"))), String.valueOf(map.get("export")), integer(map.get("amount")),
                number(map.get("price")), number(map.get("tariffs")), ((Number) map.get("completed-at")).longValue(),
                CaravanStatus.valueOf(String.valueOf(map.get("status"))));
    }
    private int integer(Object value) { return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value)); }
    private double number(Object value) { return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value)); }
    private void warn(String part, Exception exception) { plugin.getLogger().warning("Пропущены повреждённые данные (" + part + "): " + exception.getMessage()); }
}
