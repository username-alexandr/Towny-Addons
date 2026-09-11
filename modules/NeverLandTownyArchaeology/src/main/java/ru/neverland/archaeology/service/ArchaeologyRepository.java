package ru.neverland.archaeology.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.archaeology.model.DigSite;
import ru.neverland.archaeology.model.PlayerJournal;
import ru.neverland.archaeology.model.TownMuseumData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ArchaeologyRepository {
    private final JavaPlugin plugin; private final File file; private final Map<UUID, TownMuseumData> towns = new LinkedHashMap<>(); private final Map<UUID, PlayerJournal> players = new LinkedHashMap<>(); private final Map<UUID, DigSite> sites = new LinkedHashMap<>(); private final Set<String> acceptedSerials = new LinkedHashSet<>(); private final Map<String, Integer> acceptedBatches = new LinkedHashMap<>(); private boolean dirty;
    public ArchaeologyRepository(JavaPlugin plugin) { this.plugin = plugin; this.file = new File(plugin.getDataFolder(), "data.yml"); }
    private boolean writable;
    public ArchaeologyRepository(java.nio.file.Path path) { this.plugin = null; this.file = path.toFile(); }
    public boolean writable() { return writable && ru.neverland.core.AtomicFiles.writable(file.toPath()); }
    private void requireWritable() { if (!writable()) throw new IllegalStateException("Запись археологии остановлена; восстановите data.yml и перезапустите плагин"); }
    public void load() {
        writable = false;
        Map<UUID,TownMuseumData> newTowns = new LinkedHashMap<>();
        Map<UUID,PlayerJournal> newPlayers = new LinkedHashMap<>();
        Map<UUID,DigSite> newSites = new LinkedHashMap<>();
        Set<String> serials = new LinkedHashSet<>(); Map<String,Integer> batches = new LinkedHashMap<>();
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            if (file.exists()) yaml.load(file);
            if (yaml.contains("schema") && count(yaml.get("schema")) != 1) throw new IllegalArgumentException("Неизвестная версия данных");
            if (!yaml.getKeys(false).isEmpty() && java.util.Collections.disjoint(yaml.getKeys(false), Set.of("schema","towns","players","sites","accepted-serials","accepted-batches"))) throw new IllegalArgumentException("Неизвестный формат данных");
            ConfigurationSection root = section(yaml,"towns");
            if (root != null) for (String key : root.getKeys(false)) {
                UUID id = UUID.fromString(key); ConfigurationSection c = section(root,key);
                if(c==null)throw new IllegalArgumentException("Нет данных музея");
                var d = new TownMuseumData(id, string(c.get("name",key))); d.points(count(c.get("points",0)));
                loadIntMap(section(c,"available"),d.available()); loadIntMap(section(c,"donated"),d.donated());
                d.completedCollections().addAll(strings(c.get("completed-collections",List.of())));
                var contributors = section(c,"contributors"); if(contributors!=null)for(String player:contributors.getKeys(false))d.contributors().put(UUID.fromString(player),count(contributors.get(player)));
                newTowns.put(id,d);
            }
            root = section(yaml,"players");
            if(root!=null)for(String key:root.getKeys(false)) {
                UUID id=UUID.fromString(key);var c=section(root,key);if(c==null)throw new IllegalArgumentException("Нет журнала игрока");
                var j=new PlayerJournal(id,string(c.get("name",key)));j.totalFound(count(c.get("total-found",0)));
                loadIntMap(section(c,"discoveries"),j.discoveries());j.serials().addAll(strings(c.get("serials",List.of())));newPlayers.put(id,j);
            }
            Object rawSites=yaml.get("sites",List.of());if(!(rawSites instanceof List<?> list))throw new IllegalArgumentException("Неверный список раскопок");
            for(Object raw:list) {
                if(!(raw instanceof Map<?,?> m))throw new IllegalArgumentException("Повреждён участок раскопок");
                UUID id=UUID.fromString(string(m.get("id")));long created=integer(m.get("created-at"));if(created<0)throw new IllegalArgumentException("Отрицательная дата");
                var site=new DigSite(id,string(m.get("type")),UUID.fromString(string(m.get("world-id"))),string(m.get("world-name")),Math.toIntExact(integer(m.get("x"))),Math.toIntExact(integer(m.get("y"))),Math.toIntExact(integer(m.get("z"))),created);
                for(String block:strings(m.containsKey("blocks")?m.get("blocks"):List.of())){String[] xyz=block.split(",",-1);if(xyz.length!=3)throw new IllegalArgumentException("Повреждён блок раскопок");for(String n:xyz)Integer.parseInt(n);site.blocks().add(block);}
                Object completed=m.containsKey("completed")?m.get("completed"):false;if(!(completed instanceof Boolean flag))throw new IllegalArgumentException("Неверный статус раскопок");site.completed(flag);
                if(newSites.put(id,site)!=null)throw new IllegalArgumentException("Повторный ID раскопок");
            }
            serials.addAll(strings(yaml.get("accepted-serials",List.of())));loadIntMap(section(yaml,"accepted-batches"),batches);
        } catch (Exception ex) { throw new IllegalStateException("Повреждён data.yml археологии; исходный файл сохранён",ex); }
        towns.clear();towns.putAll(newTowns);players.clear();players.putAll(newPlayers);sites.clear();sites.putAll(newSites);
        acceptedSerials.clear();acceptedSerials.addAll(serials);acceptedBatches.clear();acceptedBatches.putAll(batches);
        dirty=false;ru.neverland.core.AtomicFiles.loaded(file.toPath());writable=true;
    }
    private static ConfigurationSection section(ConfigurationSection parent,String key) {if(!parent.contains(key))return null;var c=parent.getConfigurationSection(key);if(c==null)throw new IllegalArgumentException("Неверный раздел: "+key);return c;}
    private static String string(Object value) {if(!(value instanceof String s)||s.isBlank())throw new IllegalArgumentException("Пустое или неверное имя");return s;}
    private static long integer(Object value) {if(!(value instanceof Integer||value instanceof Long))throw new IllegalArgumentException("Ожидалось целое число");return ((Number)value).longValue();}
    private static int count(Object value) {int n=Math.toIntExact(integer(value));if(n<0)throw new IllegalArgumentException("Отрицательное количество");return n;}
    private static List<String> strings(Object raw) {if(!(raw instanceof List<?> list))throw new IllegalArgumentException("Ожидался список строк");return list.stream().map(ArchaeologyRepository::string).toList();}
    public synchronized void save() { requireWritable(); if (!dirty && file.exists()) return; YamlConfiguration yaml = new YamlConfiguration(); yaml.set("schema",1); for (TownMuseumData data : towns.values()) { String path = "towns." + data.townId(); yaml.set(path + ".name", data.townName()); yaml.set(path + ".points", data.points()); yaml.set(path + ".available", data.available()); yaml.set(path + ".donated", data.donated()); yaml.set(path + ".completed-collections", new ArrayList<>(data.completedCollections())); Map<String, Integer> contributors = new LinkedHashMap<>(); data.contributors().forEach((id, amount) -> contributors.put(id.toString(), amount)); yaml.set(path + ".contributors", contributors); } for (PlayerJournal journal : players.values()) { String path = "players." + journal.playerId(); yaml.set(path + ".name", journal.playerName()); yaml.set(path + ".total-found", journal.totalFound()); yaml.set(path + ".discoveries", journal.discoveries()); yaml.set(path + ".serials", new ArrayList<>(journal.serials())); } List<Map<String, Object>> siteList = new ArrayList<>(); for (DigSite site : sites.values()) { Map<String, Object> map = new LinkedHashMap<>(); map.put("id", site.id().toString()); map.put("type", site.type()); map.put("world-id", site.worldId().toString()); map.put("world-name", site.worldName()); map.put("x", site.x()); map.put("y", site.y()); map.put("z", site.z()); map.put("created-at", site.createdAt()); map.put("completed", site.completed()); map.put("blocks", new ArrayList<>(site.blocks())); siteList.add(map); } yaml.set("sites", siteList); yaml.set("accepted-serials", new ArrayList<>(acceptedSerials)); yaml.set("accepted-batches", acceptedBatches); try { ru.neverland.core.AtomicFiles.write(file.toPath(),yaml::saveToString); dirty = false; } catch (IOException exception) { writable=false; throw new java.io.UncheckedIOException(exception); } }
    public TownMuseumData museum(UUID townId, String name) { requireWritable(); TownMuseumData data = towns.computeIfAbsent(townId, id -> { dirty = true; return new TownMuseumData(id, name); }); data.townName(name); return data; } public TownMuseumData museum(UUID townId) { return towns.get(townId); }
    public PlayerJournal journal(UUID playerId, String name) { requireWritable(); PlayerJournal journal = players.computeIfAbsent(playerId, id -> { dirty = true; return new PlayerJournal(id, name); }); journal.playerName(name); return journal; } public PlayerJournal journal(UUID playerId) { return players.get(playerId); }
    public Map<UUID, DigSite> sites() { return java.util.Collections.unmodifiableMap(sites); } public void addSite(DigSite site) { requireWritable(); sites.put(site.id(), site); dirty = true; } public void removeSite(UUID id) { requireWritable(); sites.remove(id); dirty = true; }
    public boolean accepted(String serial) { return acceptedCount(serial) > 0; } public void accept(String serial) { requireWritable(); acceptedSerials.add(serial); dirty = true; }
    public int acceptedCount(String serial) { return acceptedSerials.contains(serial) ? Integer.MAX_VALUE : acceptedBatches.getOrDefault(serial, 0); }
    public void accept(String serial, int amount) { requireWritable(); if (amount <= 0) return; acceptedBatches.merge(serial, amount, Math::addExact); dirty = true; }
    public void dirty() { requireWritable(); dirty = true; } public boolean dirtyState() { return dirty; }
    private void loadIntMap(ConfigurationSection section, Map<String, Integer> target) { if (section != null) for (String key : section.getKeys(false)) target.put(key, count(section.get(key))); }
    private int number(Object value) { return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value)); } private long longNumber(Object value) { return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value)); }
}
