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
   private final File file;
   private final Map<UUID, TradeOffer> offers = new LinkedHashMap<>();
   private final Map<UUID, Caravan> caravans = new LinkedHashMap<>();
   private final Map<UUID, Double> tariffs = new LinkedHashMap<>();
   private final List<TradeHistory> history = new ArrayList<>();
   private final Map<UUID, Double> pendingCredits = new LinkedHashMap<>();
   private final CaravanEffects effects;

   public record Image(Map<UUID,TradeOffer> offers,Map<UUID,Caravan> caravans,Map<UUID,Double> tariffs,List<TradeHistory> history,Map<UUID,Double> pendingCredits) {
      public Image {offers=Map.copyOf(offers);var copies=new LinkedHashMap<UUID,Caravan>();caravans.forEach((id,c)->copies.put(id,c.copy()));caravans=Collections.unmodifiableMap(copies);tariffs=Map.copyOf(tariffs);history=List.copyOf(history);pendingCredits=Map.copyOf(pendingCredits);}
   }
   private final ru.neverland.core.AcknowledgedState<Image> acknowledged;
   public CaravanEffects effects(){return effects;}
   public boolean writable(){return acknowledged.writable()&&effects.writable();}
   public boolean busy(){return acknowledged.busy()||effects.busy();}
   public TradeRepository(JavaPlugin plugin){this(plugin.getDataFolder().toPath());}
   public TradeRepository(java.nio.file.Path folder){
      this.file=folder.resolve("trade-data.yml").toFile();effects=new CaravanEffects(folder.resolve("caravan-effects.yml"));
      acknowledged=new ru.neverland.core.AcknowledgedState<>(file.toPath(),new Image(Map.of(),Map.of(),Map.of(),List.of(),Map.of()),this::snapshot,"nlt-trade-state");
   }
   public void load(){
      if(ru.neverland.core.SuiteState.held())throw new IllegalStateException("Чтение торговли должно быть вне общей блокировки");
      offers.clear();caravans.clear();tariffs.clear();history.clear();pendingCredits.clear();
      var yaml=new YamlConfiguration();try{if(java.nio.file.Files.exists(file.toPath()))yaml.load(file);}catch(Exception error){throw new IllegalStateException("Повреждён файл торговли; загрузка остановлена",error);}
      if(yaml.contains("schema")&&SafeYaml.integer(yaml,"schema")!=2L)throw new IllegalArgumentException("Неизвестная схема торговли");
      if(!Set.of("schema","offers","caravans","tariffs","history","pending-town-credits").containsAll(yaml.getKeys(false)))throw new IllegalArgumentException("Неизвестные разделы торговли");
      loadOffers(SafeYaml.section(yaml,"offers"));loadCaravans(SafeYaml.section(yaml,"caravans"));
      var tariffRoot=SafeYaml.section(yaml,"tariffs");if(tariffRoot!=null)for(String id:tariffRoot.getKeys(false))tariffs.put(UUID.fromString(id),SafeYaml.money(tariffRoot,id));
      for(Map<?,?> raw:SafeYaml.maps(yaml,"history"))history.add(history(raw));
      var pending=SafeYaml.section(yaml,"pending-town-credits");if(pending!=null)for(String id:pending.getKeys(false))pendingCredits.put(UUID.fromString(id),SafeYaml.money(pending,id));
      try{effects.load();}catch(IOException ex){throw new UncheckedIOException(ex);}
      acknowledged.loaded(new Image(offers,caravans,tariffs,history,pendingCredits));
      offers.clear();caravans.clear();tariffs.clear();history.clear();pendingCredits.clear();
   }
   public Image image(){try(var guard=ru.neverland.core.SuiteState.guard()){var x=acknowledged.get();return new Image(x.offers(),x.caravans(),x.tariffs(),x.history(),x.pendingCredits());}}
   public java.util.concurrent.CompletionStage<Void> change(java.util.function.UnaryOperator<Image> edit){try(var guard=ru.neverland.core.SuiteState.guard()){
      var before=acknowledged.get();if(busy())return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Предыдущее изменение ещё сохраняется"));
      var detached=new Image(before.offers(),before.caravans(),before.tariffs(),before.history(),before.pendingCredits());var next=edit.apply(detached);
      if(next.offers().size()>5000&&next.offers().size()>before.offers().size()||next.caravans().size()>5000&&next.caravans().size()>before.caravans().size()||next.history().size()>100_000&&next.history().size()>before.history().size())throw new IllegalStateException("Журнал торговли достиг предела; нужна проверка администратора");
      return acknowledged.commit(before,new Image(next.offers(),next.caravans(),next.tariffs(),next.history(),next.pendingCredits())).thenApply(x->null);
   }}
   public java.util.concurrent.CompletionStage<Void> putOffer(TradeOffer value){return change(x->{var m=new LinkedHashMap<>(x.offers());m.put(value.id(),value);return new Image(m,x.caravans(),x.tariffs(),x.history(),x.pendingCredits());});}
   public java.util.concurrent.CompletionStage<Void> removeOffer(TradeOffer value){return change(x->{var m=new LinkedHashMap<>(x.offers());m.remove(value.id());return new Image(m,x.caravans(),x.tariffs(),x.history(),x.pendingCredits());});}
   public java.util.concurrent.CompletionStage<Void> accept(TradeOffer offer,Caravan value){return change(x->{var o=new LinkedHashMap<>(x.offers());if(!offer.equals(o.remove(offer.id())))throw new IllegalStateException("Предложение уже изменилось");var c=new LinkedHashMap<>(x.caravans());if(c.putIfAbsent(value.id(),value)!=null)throw new IllegalStateException("Караван уже существует");return new Image(o,c,x.tariffs(),x.history(),x.pendingCredits());});}
   public java.util.concurrent.CompletionStage<Void> update(Caravan value){return change(x->{var c=new LinkedHashMap<>(x.caravans());if(!c.containsKey(value.id()))throw new IllegalStateException("Караван не найден");c.put(value.id(),value);return new Image(x.offers(),c,x.tariffs(),x.history(),x.pendingCredits());});}
   public java.util.concurrent.CompletionStage<Void> removeCaravan(Caravan value){return change(x->{var c=new LinkedHashMap<>(x.caravans());c.remove(value.id());return new Image(x.offers(),c,x.tariffs(),x.history(),x.pendingCredits());});}
   public java.util.concurrent.CompletionStage<Void> setTariff(UUID town,double value){return change(x->{var m=new LinkedHashMap<>(x.tariffs());m.put(town,value);return new Image(x.offers(),x.caravans(),m,x.history(),x.pendingCredits());});}
   public java.util.concurrent.CompletionStage<Void> clearPending(UUID town){return change(x->{var m=new LinkedHashMap<>(x.pendingCredits());m.remove(town);return new Image(x.offers(),x.caravans(),x.tariffs(),x.history(),m);});}
   public java.util.concurrent.CompletionStage<Void> finish(Caravan value,TradeHistory entry,int limit){return change(x->{var c=new LinkedHashMap<>(x.caravans());c.put(value.id(),value);var h=new ArrayList<>(x.history());h.removeIf(e->e.caravanId().equals(entry.caravanId()));h.add(0,entry);var counts=new LinkedHashMap<UUID,Integer>();int perTown=Math.max(1,limit);h.removeIf(e->{int a=counts.merge(e.sellerId(),1,Integer::sum),b=counts.merge(e.buyerId(),1,Integer::sum);return a>perTown&&b>perTown;});return new Image(x.offers(),c,x.tariffs(),h,x.pendingCredits());});}
   public java.util.concurrent.CompletionStage<Void> expire(long now){return change(x->{var o=new LinkedHashMap<>(x.offers());o.values().removeIf(e->now>=e.expiresAt());return new Image(o,x.caravans(),x.tariffs(),x.history(),x.pendingCredits());});}
   public java.util.concurrent.CompletionStage<Void> prepareClose(){return acknowledged.prepareClose().thenCompose(x->effects.prepareClose());}

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
                        SafeYaml.longValue(root, path + "expires-at"),
                        guarantee(root,path),root.contains(path+"quoted-base-cents")?SafeYaml.longValue(root,path+"quoted-base-cents"):0,root.getString(path+"quoted-definition","")
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
               Caravan c = this.caravans.get(id);
               if(root.contains(path+"guarantee"))c.restoreGuarantee(guarantee(root,path),SafeYaml.longValue(root,path+"guarantee.duration"),SafeYaml.longValue(root,path+"guarantee.natural-delay"),SafeYaml.longValue(root,path+"guarantee.frozen-delay"),SafeYaml.longValue(root,path+"guarantee.compensation"));
               c.timer(ru.neverland.core.ActivityTimer.read(root,path,c.timer()));
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

   public Collection<TradeOffer> offers(){return acknowledged.get().offers().values();}
   public Collection<Caravan> caravans(){return acknowledged.get().caravans().values().stream().filter(c->!c.terminal()).map(Caravan::copy).toList();}
   public Collection<Caravan> allCaravans(){return acknowledged.get().caravans().values().stream().map(Caravan::copy).toList();}
   public List<TradeOffer> incoming(UUID town){return offers().stream().filter(o->o.buyerId().equals(town)).toList();}
   public List<TradeOffer> outgoing(UUID town){return offers().stream().filter(o->o.sellerId().equals(town)).toList();}
   public List<Caravan> caravans(UUID town){return caravans().stream().filter(c->c.sellerId().equals(town)||c.buyerId().equals(town)).toList();}
   public List<TradeHistory> history(UUID town){return acknowledged.get().history().stream().filter(e->e.sellerId().equals(town)||e.buyerId().equals(town)).toList();}
   public TradeOffer findOffer(String id){return find(acknowledged.get().offers(),id);}
   public Caravan findCaravan(String id){var value=find(acknowledged.get().caravans(),id);return value==null?null:value.copy();}
   private static <T>T find(Map<UUID,T> values,String raw){if(raw==null)return null;String id=raw.toLowerCase(Locale.ROOT);T found=null;for(var e:values.entrySet())if(e.getKey().toString().startsWith(id)){if(found!=null)return null;found=e.getValue();}return found;}
   public double tariff(UUID town,double fallback){return acknowledged.get().tariffs().getOrDefault(town,fallback);}
   public Map<UUID,Double> pendingCredits(){return acknowledged.get().pendingCredits();}

   private String snapshot(Image image) {
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.set("schema", 2);

      for (TradeOffer value : image.offers().values()) {
         String path = "offers." + value.id() + ".";
         yaml.set(path + "seller", value.sellerId().toString());
         yaml.set(path + "buyer", value.buyerId().toString());
         yaml.set(path + "export", value.exportId());
         yaml.set(path + "created-at", value.createdAt());
         yaml.set(path + "expires-at", value.expiresAt());
         writeGuarantee(yaml,path,value.guarantee());yaml.set(path+"quoted-base-cents",value.quotedBaseCents());yaml.set(path+"quoted-definition",value.quotedDefinition());
      }

      for (Caravan value : image.caravans().values()) {
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
         writeGuarantee(yaml,path,value.guarantee());if(value.guarantee().enabled()){yaml.set(path+"guarantee.duration",value.guaranteedRouteDuration());yaml.set(path+"guarantee.natural-delay",value.naturalDelayMillis());yaml.set(path+"guarantee.frozen-delay",value.frozenNaturalDelay());yaml.set(path+"guarantee.compensation",value.finalCompensation());}
      }

      image.tariffs().forEach((town, percent) -> yaml.set("tariffs." + town, percent));
      yaml.set("history", image.history().stream().map(this::map).toList());
      image.pendingCredits().forEach((town, amount) -> yaml.set("pending-town-credits." + town, amount));
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
      map.put("status", value.status().name());map.put("compensation-cents",value.compensationCents());
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
         CaravanStatus.valueOf(String.valueOf(map.get("status"))),map.containsKey("compensation-cents")?((Number)map.get("compensation-cents")).longValue():0
      );
   }

   private int integer(Object value) {
      return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
   }

   private double number(Object value) {
      return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
   }

   private static ru.neverland.minttrade.guarantee.GuaranteeTerms guarantee(ConfigurationSection root,String path){
      if(!root.contains(path+"guarantee"))return ru.neverland.minttrade.guarantee.GuaranteeTerms.none();
      return new ru.neverland.minttrade.guarantee.GuaranteeTerms(SafeYaml.intValue(root,path+"guarantee.reserve-bps"),SafeYaml.intValue(root,path+"guarantee.delay-bps"),SafeYaml.longValue(root,path+"guarantee.grace-ms"));
   }
   private static void writeGuarantee(YamlConfiguration y,String path,ru.neverland.minttrade.guarantee.GuaranteeTerms terms){if(!terms.enabled())return;y.set(path+"guarantee.reserve-bps",terms.reserveBasisPoints());y.set(path+"guarantee.delay-bps",terms.delayBasisPointsPerMinute());y.set(path+"guarantee.grace-ms",terms.graceMillis());}
   private void warn(String part, Exception exception) {
      throw new IllegalStateException("Повреждены данные (" + part + "); торговля остановлена", exception);
   }
}
