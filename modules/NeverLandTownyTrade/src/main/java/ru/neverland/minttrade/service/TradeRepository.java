package ru.neverland.minttrade.service;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.AtomicFiles;
import ru.neverland.core.EffectJournal;
import ru.neverland.core.SafeYaml;
import ru.neverland.minttrade.model.Caravan;
import ru.neverland.minttrade.model.CaravanStatus;
import ru.neverland.minttrade.model.RoutePoint;
import ru.neverland.minttrade.model.TradeHistory;
import ru.neverland.minttrade.model.TradeOffer;
import ru.neverland.minttrade.model.RoutePoint.Kind;

public final class TradeRepository {
   private final JavaPlugin plugin;
   private final File file;
   private final Map<UUID, TradeOffer> offers = new LinkedHashMap<>();
   private final Map<UUID, Caravan> caravans = new LinkedHashMap<>();
   private final Map<UUID, Double> tariffs = new LinkedHashMap<>();
   private final List<TradeHistory> history = new ArrayList<>();
   private final Map<UUID, Double> pendingCredits = new LinkedHashMap<>();
   private boolean dirty;
   private boolean writable;
   private final EffectJournal effects;

   public EffectJournal effects() {
      return this.effects;
   }

   public boolean writable() {
      return this.writable && AtomicFiles.writable(this.file.toPath()) && this.effects.writable();
   }

   private void gate() {
      if (!this.writable()) {
         throw new IllegalStateException("Хранилище торговли заблокировано после ошибки записи");
      }
   }

   public TradeRepository(JavaPlugin plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "trade-data.yml");
      this.effects = new EffectJournal(plugin.getDataFolder().toPath().resolve("caravan-effects.yml"));
   }

   public void load() {
      this.writable = false;
      this.offers.clear();
      this.caravans.clear();
      this.tariffs.clear();
      this.history.clear();
      this.pendingCredits.clear();
      YamlConfiguration yaml = SafeYaml.load(this.file.toPath());
      if (yaml.contains("schema") && SafeYaml.integer(yaml, "schema") != 2L) {
         throw new IllegalArgumentException("Неизвестная схема торговли");
      } else if (!Set.of("schema", "offers", "caravans", "tariffs", "history", "pending-town-credits").containsAll(yaml.getKeys(false))) {
         throw new IllegalArgumentException("Неизвестные разделы торговли");
      } else {
         this.loadOffers(SafeYaml.section(yaml, "offers"));
         this.loadCaravans(SafeYaml.section(yaml, "caravans"));
         ConfigurationSection tariffRoot = SafeYaml.section(yaml, "tariffs");
         if (tariffRoot != null) {
            for (String id : tariffRoot.getKeys(false)) {
               this.tariffs.put(UUID.fromString(id), SafeYaml.money(tariffRoot, id));
            }
         }

         for (Map<?, ?> raw : SafeYaml.maps(yaml, "history")) {
            try {
               this.history.add(this.history(raw));
            } catch (RuntimeException var6) {
               this.warn("история", var6);
            }
         }

         ConfigurationSection pending = SafeYaml.section(yaml, "pending-town-credits");
         if (pending != null) {
            for (String id : pending.getKeys(false)) {
               this.pendingCredits.put(UUID.fromString(id), SafeYaml.money(pending, id));
            }
         }

         try {
            this.effects.load();

            for (Entry<UUID, Double> e : this.pendingCredits.entrySet()) {
               this.effects.review(EffectJournal.id("legacy-trade-credit:" + e.getKey()), "Старое зачисление городу " + e.getKey() + ": " + e.getValue());
            }
         } catch (IOException var7) {
            throw new UncheckedIOException(var7);
         }

         AtomicFiles.loaded(this.file.toPath());
         this.writable = true;
         this.dirty = true;
         this.save();
      }
   }

   private void loadOffers(ConfigurationSection root) {
      if (root != null) {
         for (String raw : root.getKeys(false)) {
            try {
               String path = raw + ".";
               UUID id = UUID.fromString(raw);
               this.offers
                  .put(
                     id,
                     new TradeOffer(
                        id,
                        UUID.fromString(SafeYaml.stringValue(root, path + "seller")),
                        UUID.fromString(SafeYaml.stringValue(root, path + "buyer")),
                        SafeYaml.stringValue(root, path + "export"),
                        SafeYaml.longValue(root, path + "created-at"),
                        SafeYaml.longValue(root, path + "expires-at")
                     )
                  );
            } catch (RuntimeException var6) {
               this.warn("предложение " + raw, var6);
            }
         }
      }
   }

   private void loadCaravans(ConfigurationSection root) {
      if (root != null) {
         for (String raw : root.getKeys(false)) {
            try {
               String path = raw + ".";
               UUID id = UUID.fromString(raw);
               Map<UUID, Double> tolls = new LinkedHashMap<>();
               ConfigurationSection tollRoot = SafeYaml.section(root, path + "tariffs");
               if (tollRoot != null) {
                  for (String town : tollRoot.getKeys(false)) {
                     tolls.put(UUID.fromString(town), SafeYaml.money(tollRoot, town));
                  }
               }

               List<RoutePoint> points = new ArrayList<>();

               for (Map<?, ?> map : SafeYaml.maps(root, path + "route")) {
                  points.add(this.point(map));
               }

               this.caravans
                  .put(
                     id,
                     new Caravan(
                        id,
                        UUID.fromString(SafeYaml.stringValue(root, path + "seller")),
                        UUID.fromString(SafeYaml.stringValue(root, path + "buyer")),
                        SafeYaml.stringValue(root, path + "export"),
                        root.getItemStack(path + "cargo-item"),
                        SafeYaml.intValue(root, path + "total-cargo"),
                        SafeYaml.intValue(root, path + "remaining-cargo"),
                        SafeYaml.doubleValue(root, path + "base-price"),
                        SafeYaml.doubleValue(root, path + "escrow"),
                        tolls,
                        points,
                        SafeYaml.longValue(root, path + "departed-at"),
                        SafeYaml.longValue(root, path + "arrives-at"),
                        SafeYaml.longValue(root, path + "incident-at"),
                        SafeYaml.booleanValue(root, path + "should-delay"),
                        SafeYaml.booleanValue(root, path + "incident-handled"),
                        CaravanStatus.valueOf(SafeYaml.stringValue(root, path + "status", "ACTIVE"))
                     )
                  );
               Caravan c = this.caravans.get(id); c.timer(ru.neverland.core.ActivityTimer.read(root,path,c.timer()));
               c.settlement(root.contains(path + "settlement") ? SafeYaml.stringValue(root, path + "settlement") : "LEGACY_REVIEW");
               c.legacyFunded(SafeYaml.booleanValue(root, path + "legacy-funded", !root.contains(path + "settlement")));
               if (c.remainingCargo() < 0
                  || c.totalCargo() < 1
                  || c.totalCargo() > 1000000
                  || c.remainingCargo() > c.totalCargo()
                  || !Double.isFinite(c.basePrice())
                  || c.basePrice() < 0.0
                  || !Double.isFinite(c.escrow())
                  || c.escrow() < c.basePrice()
                  || Math.abs(c.escrow() - c.basePrice() - c.tariffs().values().stream().mapToDouble(Double::doubleValue).sum()) > 0.005) {
                  throw new IllegalArgumentException("Повреждены условия каравана");
               }
            } catch (RuntimeException var11) {
               this.warn("караван " + raw, var11);
            }
         }
      }
   }

   public Collection<TradeOffer> offers() {
      return Collections.unmodifiableCollection(this.offers.values());
   }

   public Collection<Caravan> caravans() {
      return this.caravans.values().stream().filter(c -> !c.terminal()).toList();
   }

   public Collection<Caravan> allCaravans() {
      return List.copyOf(this.caravans.values());
   }

   public List<TradeOffer> incoming(UUID townId) {
      return this.offers.values().stream().filter(value -> value.buyerId().equals(townId)).toList();
   }

   public List<TradeOffer> outgoing(UUID townId) {
      return this.offers.values().stream().filter(value -> value.sellerId().equals(townId)).toList();
   }

   public List<Caravan> caravans(UUID townId) {
      return this.caravans().stream().filter(value -> value.sellerId().equals(townId) || value.buyerId().equals(townId)).toList();
   }

   public List<TradeHistory> history(UUID townId) {
      return this.history.stream().filter(value -> value.sellerId().equals(townId) || value.buyerId().equals(townId)).toList();
   }

   public TradeOffer findOffer(String id) {
      return this.find(this.offers, id);
   }

   public Caravan findCaravan(String id) {
      return this.find(this.caravans, id);
   }

   private <T> T find(Map<UUID, T> values, String raw) {
      if (raw == null) {
         return null;
      } else {
         String id = raw.toLowerCase(Locale.ROOT);
         T found = null;

         for (Entry<UUID, T> entry : values.entrySet()) {
            if (entry.getKey().toString().startsWith(id)) {
               if (found != null) {
                  return null;
               }

               found = entry.getValue();
            }
         }

         return found;
      }
   }

   public void add(TradeOffer value) {
      this.gate();
      this.offers.put(value.id(), value);
      this.dirty = true;
   }

   public void remove(TradeOffer value) {
      this.gate();
      if (value != null) {
         this.offers.remove(value.id());
         this.dirty = true;
      }
   }

   public void add(Caravan value) {
      this.gate();
      this.caravans.put(value.id(), value);
      this.dirty = true;
   }

   public void remove(Caravan value) {
      this.gate();
      if (value != null) {
         this.caravans.remove(value.id());
         this.dirty = true;
      }
   }

   public void changed() {
      this.gate();
      this.dirty = true;
   }

   public double tariff(UUID townId, double fallback) {
      return this.tariffs.getOrDefault(townId, fallback);
   }

   public void setTariff(UUID townId, double value) {
      this.gate();
      this.tariffs.put(townId, value);
      this.dirty = true;
   }

   public void addHistory(TradeHistory entry, int limit) {
      this.gate();
      this.history.add(0, entry);
      int perTown = Math.max(1, limit);
      Map<UUID, Integer> counts = new LinkedHashMap<>();
      this.history.removeIf(value -> {
         int seller = counts.merge(value.sellerId(), 1, Integer::sum);
         int buyer = counts.merge(value.buyerId(), 1, Integer::sum);
         return seller > perTown && buyer > perTown;
      });
      this.dirty = true;
   }

   public Map<UUID, Double> pendingCredits() {
      return Map.copyOf(this.pendingCredits);
   }

   public void addPending(UUID townId, double amount) {
      this.gate();
      if (amount > 0.0) {
         this.pendingCredits.merge(townId, amount, Double::sum);
         this.dirty = true;
      }
   }

   public void clearPending(UUID townId) {
      this.gate();
      this.pendingCredits.remove(townId);
      this.dirty = true;
   }

   public boolean saveIfDirty() {
      return !this.dirty || this.save();
   }

   public boolean save() {
      this.gate();

      try {
         AtomicFiles.write(this.file.toPath(), this::snapshot);
         this.dirty = false;
         return true;
      } catch (IOException var2) {
         this.writable = false;
         throw new UncheckedIOException(var2);
      }
   }

   private String snapshot() {
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.set("schema", 2);

      for (TradeOffer value : this.offers.values()) {
         String path = "offers." + value.id() + ".";
         yaml.set(path + "seller", value.sellerId().toString());
         yaml.set(path + "buyer", value.buyerId().toString());
         yaml.set(path + "export", value.exportId());
         yaml.set(path + "created-at", value.createdAt());
         yaml.set(path + "expires-at", value.expiresAt());
      }

      for (Caravan value : this.caravans.values()) {
         String path = "caravans." + value.id() + ".";
         yaml.set(path + "settlement", value.settlement());
         yaml.set(path + "legacy-funded", value.legacyFunded());
         yaml.set(path + "seller", value.sellerId().toString());
         yaml.set(path + "buyer", value.buyerId().toString());
         yaml.set(path + "export", value.exportId());
         yaml.set(path + "cargo-item", value.cargoItem());
         yaml.set(path + "total-cargo", value.totalCargo());
         yaml.set(path + "remaining-cargo", value.remainingCargo());
         yaml.set(path + "base-price", value.basePrice());
         yaml.set(path + "escrow", value.escrow());
         value.tariffs().forEach((town, amount) -> yaml.set(path + "tariffs." + town, amount));
         yaml.set(path + "route", value.route().stream().map(this::map).toList());
         yaml.set(path + "departed-at", value.departedAt());
         yaml.set(path + "arrives-at", value.arrivesAt()); value.timer().write(yaml,path);
         yaml.set(path + "incident-at", value.incidentAt());
         yaml.set(path + "should-delay", value.shouldDelay());
         yaml.set(path + "incident-handled", value.incidentHandled());
         yaml.set(path + "status", value.status().name());
      }

      this.tariffs.forEach((town, percent) -> yaml.set("tariffs." + town, percent));
      yaml.set("history", this.history.stream().map(this::map).toList());
      this.pendingCredits.forEach((town, amount) -> yaml.set("pending-town-credits." + town, amount));
      return yaml.saveToString();
   }

   private Map<String, Object> map(RoutePoint point) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("world-id", point.worldId().toString());
      map.put("world-name", point.worldName());
      map.put("x", point.x());
      map.put("y", point.y());
      map.put("z", point.z());
      map.put("kind", point.kind().name());
      map.put("label", point.label());
      map.put("level", point.level());
      if (point.ownerId() != null) {
         map.put("owner", point.ownerId().toString());
      }

      return map;
   }

   private RoutePoint point(Map<?, ?> map) {
      Object owner = map.get("owner");
      return new RoutePoint(
         UUID.fromString(String.valueOf(map.get("world-id"))),
         String.valueOf(map.get("world-name")),
         this.number(map.get("x")),
         this.number(map.get("y")),
         this.number(map.get("z")),
         Kind.valueOf(String.valueOf(map.get("kind"))),
         String.valueOf(map.get("label")),
         this.integer(map.get("level")),
         owner == null ? null : UUID.fromString(String.valueOf(owner))
      );
   }

   private Map<String, Object> map(TradeHistory value) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("caravan", value.caravanId().toString());
      map.put("seller", value.sellerId().toString());
      map.put("buyer", value.buyerId().toString());
      map.put("export", value.exportId());
      map.put("amount", value.amount());
      map.put("price", value.price());
      map.put("tariffs", value.tariffs());
      map.put("completed-at", value.completedAt());
      map.put("status", value.status().name());
      return map;
   }

   private TradeHistory history(Map<?, ?> map) {
      return new TradeHistory(
         UUID.fromString(String.valueOf(map.get("caravan"))),
         UUID.fromString(String.valueOf(map.get("seller"))),
         UUID.fromString(String.valueOf(map.get("buyer"))),
         String.valueOf(map.get("export")),
         this.integer(map.get("amount")),
         this.number(map.get("price")),
         this.number(map.get("tariffs")),
         ((Number)map.get("completed-at")).longValue(),
         CaravanStatus.valueOf(String.valueOf(map.get("status")))
      );
   }

   private int integer(Object value) {
      return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
   }

   private double number(Object value) {
      return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
   }

   private void warn(String part, Exception exception) {
      throw new IllegalStateException("Повреждены данные (" + part + "); торговля остановлена", exception);
   }
}
