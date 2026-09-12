package ru.neverland.reputation.service;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.AtomicFiles;
import ru.neverland.core.SafeYaml;
import ru.neverland.reputation.model.RelationKey;
import ru.neverland.reputation.model.ReputationHistory;
import ru.neverland.reputation.model.ReputationRecord;
import ru.neverland.reputation.model.ReputationScope;

public final class ReputationRepository {
   private boolean ready;
   private final JavaPlugin plugin;
   private final File file;
   private final Map<RelationKey, ReputationRecord> records = new LinkedHashMap<>();
   private final Map<String, Long> processed = new LinkedHashMap<>();
   private final Map<String, Integer> dailyUsage = new LinkedHashMap<>();
   private final Map<String, Long> feedbackCooldowns = new LinkedHashMap<>();
   private final Map<String, Integer> feedbackDaily = new LinkedHashMap<>();
   private String lastDecay = "";
   private boolean dirty;

   private void loaded() {
      AtomicFiles.loaded(this.file.toPath());
      this.ready = true;
   }

   private void gate() {
      if (!this.ready || !AtomicFiles.writable(this.file.toPath())) {
         throw new IllegalStateException("Хранилище заблокировано: восстановите данные и перезапустите сервер");
      }
   }

   public ReputationRepository(JavaPlugin plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "data.yml");
   }

   public void load() {
      this.ready = false;
      this.records.clear();
      this.processed.clear();
      this.dailyUsage.clear();
      this.feedbackCooldowns.clear();
      this.feedbackDaily.clear();
      YamlConfiguration yaml = SafeYaml.load(this.file.toPath());
      SafeYaml.keys(yaml, "relations", "processed", "daily-usage", "feedback-cooldowns", "feedback-daily", "last-decay");

      for (Map<?, ?> raw : SafeYaml.maps(yaml, "relations")) {
         try {
            ReputationScope scope = ReputationScope.parse(String.valueOf(raw.get("scope")));
            UUID first = UUID.fromString(String.valueOf(raw.get("first")));
            UUID second = UUID.fromString(String.valueOf(raw.get("second")));
            ReputationRecord record = new ReputationRecord(
               RelationKey.of(scope, first, second),
               this.string(raw.get("first-name"), first.toString()),
               this.string(raw.get("second-name"), second.toString())
            );
            record.score(this.number(raw.get("score"), 0));
            record.lastChanged(this.longNumber(raw.get("last-changed"), 0L));
            Object history = raw.get("history");
            if (history != null && !(history instanceof List)) {
               throw new IllegalArgumentException("Повреждена история репутации");
            }

            if (history instanceof List) {
               for (Object entry : (List)history) {
                  if (!(entry instanceof Map<?, ?> map)) {
                     throw new IllegalArgumentException("Повреждена запись репутации");
                  }

                  String actor = this.string(map.get("actor-id"), "");
                  UUID actorId = actor.isBlank() ? null : UUID.fromString(actor);
                  record.history()
                     .add(
                        new ReputationHistory(
                           this.longNumber(map.get("timestamp"), 0L),
                           this.number(map.get("old-score"), 0),
                           this.number(map.get("new-score"), 0),
                           this.number(map.get("delta"), 0),
                           this.string(map.get("source"), "unknown"),
                           this.string(map.get("reason"), ""),
                           actorId,
                           this.string(map.get("actor-name"), "Система")
                        )
                     );
               }
            }

            this.records.put(record.key(), record);
         } catch (RuntimeException var15) {
            throw new IllegalArgumentException("Повреждены сохранённые данные: ReputationRepository");
         }
      }

      this.loadLongMap(SafeYaml.maps(yaml, "processed"), this.processed, "key", "timestamp");
      this.loadIntMap(SafeYaml.maps(yaml, "daily-usage"), this.dailyUsage, "key", "amount");
      this.loadLongMap(SafeYaml.maps(yaml, "feedback-cooldowns"), this.feedbackCooldowns, "key", "timestamp");
      this.loadIntMap(SafeYaml.maps(yaml, "feedback-daily"), this.feedbackDaily, "key", "amount");
      this.lastDecay = SafeYaml.stringValue(yaml, "last-decay", "");
      this.dirty = false;
      this.loaded();
   }

   public synchronized void save() {
      this.gate();
      if (this.dirty || !this.file.exists()) {
         YamlConfiguration yaml = new YamlConfiguration();
         List<Map<String, Object>> relationList = new ArrayList<>();

         for (ReputationRecord record : this.records.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("scope", record.key().scope().name());
            map.put("first", record.key().first().toString());
            map.put("second", record.key().second().toString());
            map.put("first-name", record.firstName());
            map.put("second-name", record.secondName());
            map.put("score", record.score());
            map.put("last-changed", record.lastChanged());
            List<Map<String, Object>> history = new ArrayList<>();

            for (ReputationHistory entry : record.history()) {
               Map<String, Object> value = new LinkedHashMap<>();
               value.put("timestamp", entry.timestamp());
               value.put("old-score", entry.oldScore());
               value.put("new-score", entry.newScore());
               value.put("delta", entry.delta());
               value.put("source", entry.source());
               value.put("reason", entry.reason());
               value.put("actor-id", entry.actorId() == null ? "" : entry.actorId().toString());
               value.put("actor-name", entry.actorName());
               history.add(value);
            }

            map.put("history", history);
            relationList.add(map);
         }

         yaml.set("relations", relationList);
         yaml.set("processed", this.longList(this.processed, "timestamp"));
         yaml.set("daily-usage", this.intList(this.dailyUsage, "amount"));
         yaml.set("feedback-cooldowns", this.longList(this.feedbackCooldowns, "timestamp"));
         yaml.set("feedback-daily", this.intList(this.feedbackDaily, "amount"));
         yaml.set("last-decay", this.lastDecay);

         try {
            AtomicFiles.write(this.file.toPath(), yaml::saveToString);
            this.dirty = false;
         } catch (IOException var10) {
            throw new UncheckedIOException(var10);
         }
      }
   }

   public ReputationRecord get(RelationKey key) {
      this.gate();
      return this.records.get(key);
   }

   public ReputationRecord getOrCreate(RelationKey key, String firstName, String secondName) {
      this.gate();
      ReputationRecord result = this.records.computeIfAbsent(key, value -> new ReputationRecord(value, firstName, secondName));
      result.names(firstName, secondName);
      return result;
   }

   public List<ReputationRecord> all() {
      this.gate();
      return List.copyOf(this.records.values());
   }

   public List<ReputationRecord> involving(ReputationScope scope, UUID id) {
      this.gate();
      return this.records
         .values()
         .stream()
         .filter(record -> record.key().scope() == scope && record.key().involves(id))
         .sorted(Comparator.<ReputationRecord>comparingInt(record -> record.score()).reversed())
         .toList();
   }

   public boolean processed(String key) {
      this.gate();
      return key != null && !key.isBlank() && this.processed.containsKey(key);
   }

   public void markProcessed(String key, long now) {
      this.gate();
      if (key != null && !key.isBlank()) {
         this.processed.put(key, now);
         this.dirty = true;
      }
   }

   public int dailyUsage(String key) {
      this.gate();
      return this.dailyUsage.getOrDefault(key, 0);
   }

   public void dailyUsage(String key, int value) {
      this.gate();
      this.dailyUsage.put(key, value);
      this.dirty = true;
   }

   public long feedbackCooldown(String key) {
      this.gate();
      return this.feedbackCooldowns.getOrDefault(key, 0L);
   }

   public void feedbackCooldown(String key, long value) {
      this.gate();
      this.feedbackCooldowns.put(key, value);
      this.dirty = true;
   }

   public int feedbackDaily(String key) {
      this.gate();
      return this.feedbackDaily.getOrDefault(key, 0);
   }

   public void feedbackDaily(String key, int value) {
      this.gate();
      this.feedbackDaily.put(key, value);
      this.dirty = true;
   }

   public String lastDecay() {
      this.gate();
      return this.lastDecay;
   }

   public void lastDecay(String value) {
      this.gate();
      this.lastDecay = value;
      this.dirty = true;
   }

   public void dirty() {
      this.gate();
      this.dirty = true;
   }

   public boolean dirtyState() {
      this.gate();
      return this.dirty;
   }

   public void cleanup(long processedCutoff, int processedLimit, String currentDay) {
      this.gate();
      this.processed.entrySet().removeIf(entry -> entry.getValue() < processedCutoff);

      while (this.processed.size() > processedLimit && !this.processed.isEmpty()) {
         this.processed.remove(this.processed.keySet().iterator().next());
      }

      this.dailyUsage.keySet().removeIf(key -> !key.startsWith(currentDay + "|"));
      this.feedbackDaily.keySet().removeIf(key -> !key.startsWith(currentDay + "|"));
      this.dirty = true;
   }

   private List<Map<String, Object>> longList(Map<String, Long> source, String valueName) {
      List<Map<String, Object>> result = new ArrayList<>();
      source.forEach((key, value) -> result.add(Map.of("key", key, valueName, value)));
      return result;
   }

   private List<Map<String, Object>> intList(Map<String, Integer> source, String valueName) {
      List<Map<String, Object>> result = new ArrayList<>();
      source.forEach((key, value) -> result.add(Map.of("key", key, valueName, value)));
      return result;
   }

   private void loadLongMap(List<Map<?, ?>> list, Map<String, Long> target, String keyName, String valueName) {
      for (Map<?, ?> map : list) {
         target.put(this.string(map.get(keyName), ""), this.longNumber(map.get(valueName), 0L));
      }
   }

   private void loadIntMap(List<Map<?, ?>> list, Map<String, Integer> target, String keyName, String valueName) {
      for (Map<?, ?> map : list) {
         target.put(this.string(map.get(keyName), ""), this.number(map.get(valueName), 0));
      }
   }

   private String string(Object value, String fallback) {
      return value == null ? fallback : String.valueOf(value);
   }

   private int number(Object value, int fallback) {
      if (value == null) {
         return fallback;
      } else if (!(value instanceof Integer) && !(value instanceof Long)) {
         throw new IllegalArgumentException("Нужно целое число репутации");
      } else {
         return Math.toIntExact(((Number)value).longValue());
      }
   }

   private long longNumber(Object value, long fallback) {
      if (value == null) {
         return fallback;
      } else if (!(value instanceof Integer) && !(value instanceof Long)) {
         throw new IllegalArgumentException("Нужно целое число истории");
      } else {
         return ((Number)value).longValue();
      }
   }
}
