package ru.neverland.mintexpeditions.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.AtomicFiles;
import ru.neverland.core.EffectJournal;
import ru.neverland.core.SafeYaml;
import ru.neverland.localization.MaterialNameConfig;
import ru.neverland.mintexpeditions.model.ActiveExpedition;
import ru.neverland.mintexpeditions.model.BlockPos;
import ru.neverland.mintexpeditions.model.ExpeditionHistory;
import ru.neverland.mintexpeditions.model.ExpeditionStatus;
import ru.neverland.mintexpeditions.model.SiteSnapshot;

public final class ExpeditionRepository {
   private final Path file;
   private final Map<UUID, ActiveExpedition> active = new LinkedHashMap<>();
   private final List<ExpeditionHistory> history = new ArrayList<>();
   private final Map<UUID, RewardBatch> rewards = new LinkedHashMap<>();
   private final EffectJournal effects;
   private boolean dirty;
   private boolean writable;

   public ExpeditionRepository(JavaPlugin plugin) {
      this(plugin.getDataFolder().toPath());
   }

   public ExpeditionRepository(Path directory) {
      this.file = directory.resolve("data.yml");
      this.effects = new EffectJournal(directory.resolve("reward-effects.yml"));
   }

   public EffectJournal effects() {
      return this.effects;
   }

   public boolean writable() {
      return this.writable && AtomicFiles.writable(this.file) && this.effects.writable();
   }

   private void gate() {
      if (!this.writable()) {
         throw new IllegalStateException("Хранилище экспедиций заблокировано после ошибки; восстановите файл и перезапустите сервер");
      }
   }

   public void load() {
      this.writable = false;
      this.active.clear();
      this.history.clear();
      this.rewards.clear();
      YamlConfiguration y = SafeYaml.load(this.file);
      if (y.contains("schema") && SafeYaml.integer(y, "schema") != 2L) {
         throw new IllegalArgumentException("Неизвестная схема экспедиций");
      } else if (!Set.of("schema", "active", "history", "rewards", "reward-batches").containsAll(y.getKeys(false))) {
         throw new IllegalArgumentException("Неизвестные разделы данных экспедиций");
      } else {
         ConfigurationSection root = SafeYaml.section(y, "active");
         if (root != null) {
            for (String raw : root.getKeys(false)) {
               ConfigurationSection s = SafeYaml.section(root, raw);
               if (s == null) {
                  throw new IllegalArgumentException("Нет экспедиции");
               }

               UUID id = UUID.fromString(raw);
               ActiveExpedition e = new ActiveExpedition(
                  id,
                  UUID.fromString(SafeYaml.text(s, "leader")),
                  SafeYaml.text(s, "definition"),
                  UUID.fromString(SafeYaml.text(s, "world-id")),
                  SafeYaml.text(s, "world-name"),
                  BlockPos.parse(SafeYaml.text(s, "camp")),
                  BlockPos.parse(SafeYaml.text(s, "site")),
                  SafeYaml.integer(s, "started"),
                  SafeYaml.integer(s, "expires")
               );

               for (Object v : SafeYaml.list(s, "participants")) {
                  e.participants().add(UUID.fromString((String)v));
               }

               for (Object v : SafeYaml.list(s, "objectives")) {
                  e.objectives().add(BlockPos.parse((String)v));
               }

               for (Object v : SafeYaml.list(s, "completed")) {
                  e.completed().add(BlockPos.parse((String)v));
               }

               long kills = SafeYaml.integer(s, "kills");
               if (kills < 0L) {
                  throw new IllegalArgumentException("Отрицательные убийства");
               }

               e.kills(Math.toIntExact(kills));
               e.status(ExpeditionStatus.valueOf(s.contains("status") ? SafeYaml.text(s, "status") : "ACTIVE"));

               for (Map<?, ?> m : SafeYaml.maps(s, "snapshots")) {
                  Material material = MaterialNameConfig.matchMaterial((String)m.get("material"));
                  if (material == null) {
                     throw new IllegalArgumentException("Неизвестный материал снимка");
                  }

                  List<ItemStack> inventory = readItems(m.containsKey("inventory") ? m.get("inventory") : List.of(), true);
                  e.snapshots().put(BlockPos.parse((String)m.get("pos")), new SiteSnapshot(material, (String)m.get("data"), inventory));
               }

               this.active.put(id, e);
            }
         }

         for (Map<?, ?> m : SafeYaml.maps(y, "history")) {
            this.history
               .add(
                  new ExpeditionHistory(
                     UUID.fromString((String)m.get("id")),
                     UUID.fromString((String)m.get("leader")),
                     (String)m.get("definition"),
                     number(m, "participants"),
                     number(m, "objectives"),
                     number(m, "kills"),
                     ExpeditionStatus.valueOf((String)m.get("status")),
                     ((Number)m.get("ended")).longValue()
                  )
               );
         }

         root = SafeYaml.section(y, "reward-batches");
         if (root != null) {
            for (String key : root.getKeys(false)) {
               ConfigurationSection sx = SafeYaml.section(root, key);
               UUID id = UUID.fromString(key);
               this.rewards
                  .put(
                     id,
                     new RewardBatch(id, UUID.fromString(SafeYaml.text(sx, "owner")), readItems(SafeYaml.list(sx, "items"), false), SafeYaml.money(sx, "money"))
                  );
            }
         }

         root = SafeYaml.section(y, "rewards");
         if (root != null) {
            for (String key : root.getKeys(false)) {
               ConfigurationSection sx = SafeYaml.section(root, key);
               UUID owner = UUID.fromString(key);
               UUID id = EffectJournal.id("legacy-expedition-reward:" + owner);
               if (this.rewards.containsKey(id)) {
                  throw new IllegalArgumentException("Дублированная старая награда");
               }

               this.rewards.put(id, new RewardBatch(id, owner, readItems(SafeYaml.list(sx, "items"), false), SafeYaml.money(sx, "money")));
            }
         }

         try {
            this.effects.load();

            for (RewardBatch b : this.rewards.values()) {
               if (b.id().equals(EffectJournal.id("legacy-expedition-reward:" + b.owner()))) {
                  if (b.money() > 0.0) {
                     this.effects.review(b.moneyId(), "Награда " + b.id() + " игроку " + b.owner() + ": " + b.money());
                  }

                  for (int i = 0; i < b.items().size(); i++) {
                     this.effects.review(b.itemId(i), "Предмет награды " + b.id() + " игроку " + b.owner() + " #" + i);
                  }
               }
            }
         } catch (IOException var14) {
            throw new UncheckedIOException(var14);
         }

         for (ExpeditionHistory h : this.history) {
            ActiveExpedition e = this.active.get(h.id());
            if (e != null && e.status() == ExpeditionStatus.ACTIVE) {
               e.status(h.status());
            }
         }

         AtomicFiles.loaded(this.file);
         this.writable = true;
         this.dirty = true;
         this.save();
      }
   }

   private static int number(Map<?, ?> m, String k) {
      Object v = m.get(k);
      if (!(v instanceof Integer) && !(v instanceof Long)) {
         throw new IllegalArgumentException("Нужно целое " + k);
      } else {
         int n = Math.toIntExact(((Number)v).longValue());
         if (n < 0) {
            throw new IllegalArgumentException(k);
         } else {
            return n;
         }
      }
   }

   private static List<ItemStack> readItems(Object value, boolean nullable) {
      if (!(value instanceof List<?> list)) {
         throw new IllegalArgumentException("Повреждён список предметов");
      } else {
         ArrayList out = new ArrayList();

         for (Object o : list) {
            if (o == null && nullable) {
               out.add(null);
            } else {
               if (!(o instanceof ItemStack item)) {
                  throw new IllegalArgumentException("Повреждён предмет");
               }

               out.add(item.clone());
            }
         }

         return out;
      }
   }

   public void save() {
      this.gate();

      try {
         AtomicFiles.write(this.file, () -> this.encode().saveToString());
         this.dirty = false;
      } catch (IOException var2) {
         this.writable = false;
         throw new UncheckedIOException(var2);
      }
   }

   private YamlConfiguration encode() {
      YamlConfiguration y = new YamlConfiguration();
      y.set("schema", 2);

      for (ActiveExpedition e : this.active.values()) {
         String p = "active." + e.id() + ".";
         y.set(p + "leader", e.leaderId().toString());
         y.set(p + "definition", e.definitionId());
         y.set(p + "world-id", e.worldId().toString());
         y.set(p + "world-name", e.worldName());
         y.set(p + "camp", e.campAnchor().key());
         y.set(p + "site", e.siteAnchor().key());
         y.set(p + "started", e.startedAt());
         y.set(p + "expires", e.expiresAt());
         y.set(p + "status", e.status().name());
         y.set(p + "participants", e.participants().stream().map(UUID::toString).toList());
         y.set(p + "objectives", e.objectives().stream().map(BlockPos::key).toList());
         y.set(p + "completed", e.completed().stream().map(BlockPos::key).toList());
         y.set(p + "kills", e.kills());
         List<Map<String, Object>> snapshots = new ArrayList<>();
         e.snapshots().forEach((pos, s) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pos", pos.key());
            m.put("material", s.material().name());
            m.put("data", s.blockData());
            m.put("inventory", s.inventory());
            snapshots.add(m);
         });
         y.set(p + "snapshots", snapshots);
      }

      List<Map<String, Object>> hist = new ArrayList<>();

      for (ExpeditionHistory h : this.history) {
         hist.add(
            Map.of(
               "id",
               h.id().toString(),
               "leader",
               h.leaderId().toString(),
               "definition",
               h.definitionId(),
               "participants",
               h.participants(),
               "objectives",
               h.objectives(),
               "kills",
               h.kills(),
               "status",
               h.status().name(),
               "ended",
               h.endedAt()
            )
         );
      }

      y.set("history", hist);

      for (RewardBatch b : this.rewards.values()) {
         String p = "reward-batches." + b.id() + ".";
         y.set(p + "owner", b.owner().toString());
         y.set(p + "items", b.items());
         y.set(p + "money", b.money());
      }

      return y;
   }

   public Collection<ActiveExpedition> active() {
      return List.copyOf(this.active.values());
   }

   public ActiveExpedition get(UUID id) {
      return this.active.get(id);
   }

   public ActiveExpedition byLeader(UUID id) {
      return this.active
         .values()
         .stream()
         .filter(e -> e.status() == ExpeditionStatus.ACTIVE && (e.leaderId().equals(id) || e.participants().contains(id)))
         .findFirst()
         .orElse(null);
   }

   public ActiveExpedition byCamp(UUID id) {
      return this.active.values().stream().filter(e -> e.leaderId().equals(id)).findFirst().orElse(null);
   }

   public void put(ActiveExpedition e) {
      this.gate();
      this.active.put(e.id(), e);
      this.dirty = true;
   }

   public void remove(ActiveExpedition e) {
      this.gate();
      this.active.remove(e.id());
      this.dirty = true;
   }

   public void changed() {
      this.gate();
      this.dirty = true;
   }

   public void saveIfDirty() {
      if (this.dirty) {
         this.save();
      }
   }

   public void history(ExpeditionHistory h, int limit) {
      this.gate();
      if (this.history.stream().noneMatch(v -> v.id().equals(h.id()))) {
         this.history.add(0, h);
      }

      while (this.history.size() > Math.max(1, limit)) {
         this.history.remove(this.history.size() - 1);
      }

      this.dirty = true;
   }

   public List<ExpeditionHistory> history() {
      return List.copyOf(this.history);
   }

   public void reward(UUID id, List<ItemStack> items, double money) {
      this.reward(UUID.randomUUID(), id, items, money);
   }

   public void reward(UUID rewardId, UUID owner, List<ItemStack> items, double money) {
      this.gate();
      RewardBatch batch = new RewardBatch(rewardId, owner, items, money);
      RewardBatch old = this.rewards.get(rewardId);
      if (old != null && !old.equals(batch)) {
         throw new IllegalArgumentException("UUID награды занят другими условиями");
      } else {
         this.rewards.putIfAbsent(rewardId, batch);
         this.dirty = true;
      }
   }

   public List<RewardBatch> rewards(UUID owner) {
      return this.rewards.values().stream().filter(r -> r.owner().equals(owner)).toList();
   }

   public boolean hasReward(UUID owner) {
      return !this.rewards(owner).isEmpty();
   }

   public double money(UUID owner) {
      return this.rewards(owner).stream().filter(b -> this.effects.state(b.moneyId()) != EffectJournal.State.DONE).mapToDouble(RewardBatch::money).sum();
   }

   public void completeReward(RewardBatch batch) {
      this.gate();
      if (batch.effects().stream().anyMatch(id -> this.effects.state(id) != EffectJournal.State.DONE)) {
         throw new IllegalStateException("Награда выдана не полностью");
      } else {
         this.rewards.remove(batch.id());
         this.dirty = true;
         this.save();
      }
   }

   public void pruneEffects() throws IOException {
      Set<UUID> needed = new HashSet<>();
      this.rewards.values().forEach(r -> needed.addAll(r.effects()));
      this.effects.retain(needed);
   }
}
