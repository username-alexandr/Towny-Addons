package ru.neverland.reputation.service;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.reputation.model.RelationKey;
import ru.neverland.reputation.model.ReputationHistory;
import ru.neverland.reputation.model.ReputationRecord;
import ru.neverland.reputation.model.ReputationScope;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReputationRepository {
    private final JavaPlugin plugin; private final File file;
    private final Map<RelationKey, ReputationRecord> records = new LinkedHashMap<>();
    private final Map<String, Long> processed = new LinkedHashMap<>();
    private final Map<String, Integer> dailyUsage = new LinkedHashMap<>();
    private final Map<String, Long> feedbackCooldowns = new LinkedHashMap<>();
    private final Map<String, Integer> feedbackDaily = new LinkedHashMap<>();
    private String lastDecay = ""; private boolean dirty;
    public ReputationRepository(JavaPlugin plugin) { this.plugin = plugin; this.file = new File(plugin.getDataFolder(), "data.yml"); }

    public void load() {
        records.clear(); processed.clear(); dailyUsage.clear(); feedbackCooldowns.clear(); feedbackDaily.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> raw : yaml.getMapList("relations")) try {
            ReputationScope scope = ReputationScope.parse(String.valueOf(raw.get("scope"))); UUID first = UUID.fromString(String.valueOf(raw.get("first"))); UUID second = UUID.fromString(String.valueOf(raw.get("second")));
            ReputationRecord record = new ReputationRecord(RelationKey.of(scope, first, second), string(raw.get("first-name"), first.toString()), string(raw.get("second-name"), second.toString()));
            record.score(number(raw.get("score"), 0)); record.lastChanged(longNumber(raw.get("last-changed"), 0));
            Object history = raw.get("history"); if (history instanceof List<?> list) for (Object entry : list) if (entry instanceof Map<?, ?> map) {
                String actor = string(map.get("actor-id"), ""); UUID actorId = actor.isBlank() ? null : UUID.fromString(actor);
                record.history().add(new ReputationHistory(longNumber(map.get("timestamp"), 0), number(map.get("old-score"), 0), number(map.get("new-score"), 0), number(map.get("delta"), 0), string(map.get("source"), "unknown"), string(map.get("reason"), ""), actorId, string(map.get("actor-name"), "Система")));
            }
            records.put(record.key(), record);
        } catch (RuntimeException exception) { plugin.getLogger().warning("Пропущена повреждённая связь репутации: " + exception.getMessage()); }
        loadLongMap(yaml.getMapList("processed"), processed, "key", "timestamp");
        loadIntMap(yaml.getMapList("daily-usage"), dailyUsage, "key", "amount");
        loadLongMap(yaml.getMapList("feedback-cooldowns"), feedbackCooldowns, "key", "timestamp");
        loadIntMap(yaml.getMapList("feedback-daily"), feedbackDaily, "key", "amount");
        lastDecay = yaml.getString("last-decay", ""); dirty = false;
    }

    public synchronized void save() {
        if (!dirty && file.exists()) return;
        YamlConfiguration yaml = new YamlConfiguration(); List<Map<String, Object>> relationList = new ArrayList<>();
        for (ReputationRecord record : records.values()) {
            Map<String, Object> map = new LinkedHashMap<>(); map.put("scope", record.key().scope().name()); map.put("first", record.key().first().toString()); map.put("second", record.key().second().toString());
            map.put("first-name", record.firstName()); map.put("second-name", record.secondName()); map.put("score", record.score()); map.put("last-changed", record.lastChanged());
            List<Map<String, Object>> history = new ArrayList<>(); for (ReputationHistory entry : record.history()) { Map<String, Object> value = new LinkedHashMap<>(); value.put("timestamp", entry.timestamp()); value.put("old-score", entry.oldScore()); value.put("new-score", entry.newScore()); value.put("delta", entry.delta()); value.put("source", entry.source()); value.put("reason", entry.reason()); value.put("actor-id", entry.actorId() == null ? "" : entry.actorId().toString()); value.put("actor-name", entry.actorName()); history.add(value); } map.put("history", history); relationList.add(map);
        }
        yaml.set("relations", relationList); yaml.set("processed", longList(processed, "timestamp")); yaml.set("daily-usage", intList(dailyUsage, "amount")); yaml.set("feedback-cooldowns", longList(feedbackCooldowns, "timestamp")); yaml.set("feedback-daily", intList(feedbackDaily, "amount")); yaml.set("last-decay", lastDecay);
        try { yaml.save(file); dirty = false; } catch (IOException exception) { plugin.getLogger().severe("Не удалось сохранить data.yml: " + exception.getMessage()); }
    }

    public ReputationRecord get(RelationKey key) { return records.get(key); }
    public ReputationRecord getOrCreate(RelationKey key, String firstName, String secondName) { ReputationRecord result = records.computeIfAbsent(key, value -> new ReputationRecord(value, firstName, secondName)); result.names(firstName, secondName); return result; }
    public List<ReputationRecord> all() { return List.copyOf(records.values()); }
    public List<ReputationRecord> involving(ReputationScope scope, UUID id) { return records.values().stream().filter(record -> record.key().scope() == scope && record.key().involves(id)).sorted(Comparator.comparingInt((ReputationRecord record) -> record.score()).reversed()).toList(); }
    public boolean processed(String key) { return key != null && !key.isBlank() && processed.containsKey(key); }
    public void markProcessed(String key, long now) { if (key != null && !key.isBlank()) { processed.put(key, now); dirty = true; } }
    public int dailyUsage(String key) { return dailyUsage.getOrDefault(key, 0); }
    public void dailyUsage(String key, int value) { dailyUsage.put(key, value); dirty = true; }
    public long feedbackCooldown(String key) { return feedbackCooldowns.getOrDefault(key, 0L); }
    public void feedbackCooldown(String key, long value) { feedbackCooldowns.put(key, value); dirty = true; }
    public int feedbackDaily(String key) { return feedbackDaily.getOrDefault(key, 0); }
    public void feedbackDaily(String key, int value) { feedbackDaily.put(key, value); dirty = true; }
    public String lastDecay() { return lastDecay; }
    public void lastDecay(String value) { lastDecay = value; dirty = true; }
    public void dirty() { dirty = true; }
    public boolean dirtyState() { return dirty; }
    public void cleanup(long processedCutoff, int processedLimit, String currentDay) {
        processed.entrySet().removeIf(entry -> entry.getValue() < processedCutoff);
        while (processed.size() > processedLimit && !processed.isEmpty()) processed.remove(processed.keySet().iterator().next());
        dailyUsage.keySet().removeIf(key -> !key.startsWith(currentDay + "|")); feedbackDaily.keySet().removeIf(key -> !key.startsWith(currentDay + "|")); dirty = true;
    }
    private List<Map<String, Object>> longList(Map<String, Long> source, String valueName) { List<Map<String, Object>> result = new ArrayList<>(); source.forEach((key, value) -> result.add(Map.of("key", key, valueName, value))); return result; }
    private List<Map<String, Object>> intList(Map<String, Integer> source, String valueName) { List<Map<String, Object>> result = new ArrayList<>(); source.forEach((key, value) -> result.add(Map.of("key", key, valueName, value))); return result; }
    private void loadLongMap(List<Map<?, ?>> list, Map<String, Long> target, String keyName, String valueName) { for (Map<?, ?> map : list) target.put(string(map.get(keyName), ""), longNumber(map.get(valueName), 0)); }
    private void loadIntMap(List<Map<?, ?>> list, Map<String, Integer> target, String keyName, String valueName) { for (Map<?, ?> map : list) target.put(string(map.get(keyName), ""), number(map.get(valueName), 0)); }
    private String string(Object value, String fallback) { return value == null ? fallback : String.valueOf(value); }
    private int number(Object value, int fallback) { return value instanceof Number number ? number.intValue() : fallback; }
    private long longNumber(Object value, long fallback) { return value instanceof Number number ? number.longValue() : fallback; }
}
