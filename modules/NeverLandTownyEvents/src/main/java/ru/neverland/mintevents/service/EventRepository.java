package ru.neverland.mintevents.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintevents.model.ActiveEvent;
import ru.neverland.mintevents.model.HistoryEntry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EventRepository {
    private boolean ready;
    private void loaded(){ru.neverland.core.AtomicFiles.loaded(file.toPath());ready=true;}
    private void gate(){if(!ready||!ru.neverland.core.AtomicFiles.writable(file.toPath()))throw new IllegalStateException("Хранилище заблокировано: восстановите данные и перезапустите сервер");}

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, ActiveEvent> active = new LinkedHashMap<>();
    private final Map<UUID, List<HistoryEntry>> history = new LinkedHashMap<>();
    private final Map<UUID, Long> cooldowns = new LinkedHashMap<>();
    private boolean dirty;

    public EventRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "events-data.yml");
    }

    public synchronized void load() {ready=false;
        active.clear();
        history.clear();
        cooldowns.clear();

        YamlConfiguration yaml = ru.neverland.core.SafeYaml.load(file.toPath());ru.neverland.core.SafeYaml.keys(yaml,"active","towns");
        ConfigurationSection activeRoot = ru.neverland.core.SafeYaml.section(yaml,"active");
        if (activeRoot != null) {
            for (String raw : activeRoot.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(raw);
                    String path = "active." + raw + ".";
                    active.put(id, new ActiveEvent(id, ru.neverland.core.SafeYaml.stringValue(yaml,path + "event", ""),
                            ru.neverland.core.SafeYaml.longValue(yaml,path + "started-at"), ru.neverland.core.SafeYaml.longValue(yaml,path + "ends-at"),
                            ru.neverland.core.SafeYaml.intValue(yaml,path + "progress"), ru.neverland.core.SafeYaml.intValue(yaml,path + "goal", 1),
                            ru.neverland.core.SafeYaml.doubleValue(yaml,path + "protection"), ru.neverland.core.SafeYaml.longValue(yaml,path + "last-raid-wave")));
                    if (yaml.isConfigurationSection(path + "raid")) active.get(id).raid(
                            ru.neverland.mintevents.model.RaidState.load(ru.neverland.core.SafeYaml.section(yaml,path + "raid")));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Повреждены сохранённые данные: EventRepository");
                }
            }
        }
        ConfigurationSection townRoot = ru.neverland.core.SafeYaml.section(yaml,"towns");
        if (townRoot != null) {
            for (String raw : townRoot.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(raw);
                    cooldowns.put(id, ru.neverland.core.SafeYaml.longValue(yaml,"towns." + raw + ".last-event-at"));
                    List<HistoryEntry> entries = new ArrayList<>();
                    List<Map<?, ?>> maps = ru.neverland.core.SafeYaml.maps(yaml,"towns." + raw + ".history");
                    for (Map<?, ?> map : maps) {
                        entries.add(new HistoryEntry(text(map.get("event")), number(map.get("started-at")),
                                number(map.get("ended-at")), (int) number(map.get("progress")),
                                (int) number(map.get("goal")), Boolean.parseBoolean(text(map.get("success")))));
                    }
                    history.put(id, entries);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Повреждены сохранённые данные: EventRepository");
                }
            }
        }
        dirty = false;
    loaded();}

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private long number(Object value) { return value instanceof Number n ? n.longValue() : 0; }

    public synchronized Map<UUID, ActiveEvent> active() {gate(); return new LinkedHashMap<>(active); }
    public synchronized ActiveEvent active(UUID townId) {gate(); return active.get(townId); }
    public synchronized void put(ActiveEvent event) {gate(); active.put(event.townId(), event); dirty = true; }
    public synchronized ActiveEvent remove(UUID townId) {gate(); ActiveEvent old = active.remove(townId); dirty |= old != null; return old; }
    public synchronized List<HistoryEntry> history(UUID townId) {gate(); return List.copyOf(history.getOrDefault(townId, List.of())); }
    public synchronized long lastEventAt(UUID townId) {gate(); return cooldowns.getOrDefault(townId, 0L); }
    public synchronized void changed() {gate(); dirty = true; }

    public synchronized void complete(ActiveEvent event, boolean success, int limit, long now) {gate();
        active.remove(event.townId());
        cooldowns.put(event.townId(), now);
        List<HistoryEntry> entries = history.computeIfAbsent(event.townId(), key -> new ArrayList<>());
        entries.add(0, new HistoryEntry(event.eventId(), event.startedAt(), now, event.progress(), event.goal(), success));
        while (entries.size() > Math.max(1, limit)) entries.remove(entries.size() - 1);
        dirty = true;
    }

    public synchronized void saveIfDirty() {gate(); if (dirty) save(); }

    public synchronized void save() {gate();
        YamlConfiguration yaml = new YamlConfiguration();
        for (ActiveEvent event : active.values()) {
            String path = "active." + event.townId() + ".";
            yaml.set(path + "event", event.eventId());
            yaml.set(path + "started-at", event.startedAt());
            yaml.set(path + "ends-at", event.endsAt());
            yaml.set(path + "progress", event.progress());
            yaml.set(path + "goal", event.goal());
            yaml.set(path + "protection", event.protection());
            yaml.set(path + "last-raid-wave", event.lastRaidWave());
            if (event.raid() != null) event.raid().save(yaml.createSection(path + "raid"));
        }
        for (Map.Entry<UUID, Long> cooldown : cooldowns.entrySet()) {
            yaml.set("towns." + cooldown.getKey() + ".last-event-at", cooldown.getValue());
        }
        for (Map.Entry<UUID, List<HistoryEntry>> town : history.entrySet()) {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (HistoryEntry entry : town.getValue()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("event", entry.eventId());
                map.put("started-at", entry.startedAt());
                map.put("ended-at", entry.endedAt());
                map.put("progress", entry.progress());
                map.put("goal", entry.goal());
                map.put("success", entry.success());
                entries.add(map);
            }
            yaml.set("towns." + town.getKey() + ".history", entries);
        }
        try {
            ru.neverland.core.AtomicFiles.write(file.toPath(),yaml::saveToString);
            dirty = false;
        } catch(IOException exception){throw new java.io.UncheckedIOException(exception);}
    }
}
