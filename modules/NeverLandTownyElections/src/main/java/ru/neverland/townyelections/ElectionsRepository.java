package ru.neverland.townyelections;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.AtomicFiles;
import ru.neverland.core.SafeYaml;

public final class ElectionsRepository {
    private final Path file;
    private Map<UUID,Election> towns = Map.of();
    private boolean ready;
    public ElectionsRepository(Path file) { this.file = file; }
    public boolean healthy() { return ready && AtomicFiles.writable(file); }
    public Election get(UUID town) { var e = towns.get(town); return e == null ? null : e.copy(); }
    public Set<UUID> towns() { return Set.copyOf(towns.keySet()); }
    public void load() throws Exception {
        ready = false; var next = new LinkedHashMap<UUID,Election>();
        if (Files.exists(file)) {
            var y = new YamlConfiguration(); y.load(file.toFile()); SafeYaml.keys(y,"schema","towns");
            if (number(y,"schema") != 1) throw new IOException("Unknown elections schema");
            var root = section(y,"towns");
            for (String key : root.getKeys(false)) {
                Election e = read(section(root,key)); if (!e.town.toString().equals(key)) throw new IOException("Town identity mismatch");
                e.validate(); next.put(e.town,e);
            }
        }
        towns = Map.copyOf(next); AtomicFiles.loaded(file); ready = true;
    }
    public void put(Election election) throws IOException {
        if (!healthy()) throw new IOException("Журнал выборов заблокирован после ошибки записи; нужен перезапуск после восстановления.");
        election.validate(); var next = new LinkedHashMap<>(towns); next.put(election.town, election.copy());
        var y = new YamlConfiguration(); y.set("schema",1); var root = y.createSection("towns");
        next.forEach((id,e) -> write(root.createSection(id.toString()),e));
        try { AtomicFiles.write(file, y::saveToString); towns = Map.copyOf(next); }
        catch (IOException ex) { ready = false; throw ex; }
    }
    private static void write(ConfigurationSection s, Election e) {
        s.set("id",e.id.toString()); s.set("town",e.town.toString()); s.set("phase",e.phase.name());
        s.set("governance-before", e.governanceBefore);
        s.set("original-mayor",e.originalMayor == null ? "" : e.originalMayor.toString());
        s.set("start",e.start); s.set("nomination-end",e.nominationEnd); s.set("voting-end",e.votingEnd); s.set("next",e.next);
        s.set("admin-paused-at",e.adminPausedAt);s.set("nomination-duration",e.nominationDuration);s.set("interval",e.interval); s.set("voting-duration",e.votingDuration); s.set("quorum",e.quorum); s.set("detail",e.detail);
        s.set("electorate",e.electorate.stream().map(UUID::toString).toList()); s.createSection("seats",e.seats);
        var candidates = s.createSection("candidates"); e.candidates.forEach((id,r) -> candidates.set(id.toString(),r));
        var votes = s.createSection("ballots"); e.ballots.forEach((race,voters) -> {
            var r = votes.createSection(race); voters.forEach((v,choices) -> r.set(v.toString(), choices.stream().map(UUID::toString).toList()));
        });
        var winners = s.createSection("winners"); e.winners.forEach((r,ids) -> winners.set(r,ids.stream().map(UUID::toString).toList()));
        s.createSection("results",e.results); s.set("history",e.history);
    }
    private static Election read(ConfigurationSection s) throws IOException {
        SafeYaml.keys(s,"id","town","phase","governance-before","original-mayor","start","nomination-end","voting-end","next","interval","voting-duration","quorum","detail","electorate","seats","candidates","ballots","winners","results","history","admin-paused-at","nomination-duration");
        var e = new Election(UUID.fromString(string(s,"town")),number(s,"next")); e.id=UUID.fromString(string(s,"id"));
        e.phase=Election.Phase.valueOf(string(s,"phase")); String mayor=string(s,"original-mayor"); e.originalMayor=mayor.isEmpty()?null:UUID.fromString(mayor);
        e.governanceBefore=string(s,"governance-before"); if(!e.governanceBefore.isEmpty())UUID.fromString(e.governanceBefore);
        e.adminPausedAt=SafeYaml.longValue(s,"admin-paused-at",0);e.nominationDuration=SafeYaml.longValue(s,"nomination-duration",0);e.start=number(s,"start"); e.nominationEnd=number(s,"nomination-end"); e.votingEnd=number(s,"voting-end"); e.interval=number(s,"interval"); e.votingDuration=number(s,"voting-duration");
        if (!(s.get("quorum") instanceof Number n)) throw new IOException("Invalid quorum"); e.quorum=n.doubleValue(); e.detail=string(s,"detail");
        for (String v : strings(s,"electorate")) if (!e.electorate.add(UUID.fromString(v))) throw new IOException("Duplicate voter");
        var seats = section(s,"seats"); for (String r : seats.getKeys(false)) e.seats.put(r,Math.toIntExact(number(seats,r)));
        var candidates = section(s,"candidates"); for (String id : candidates.getKeys(false)) e.candidates.put(UUID.fromString(id),string(candidates,id));
        var votes = section(s,"ballots"); for (String r : votes.getKeys(false)) {
            var race = section(votes,r); var map = new LinkedHashMap<UUID,List<UUID>>();
            for (String v : race.getKeys(false)) map.put(UUID.fromString(v),strings(race,v).stream().map(UUID::fromString).toList());
            e.ballots.put(r,map);
        }
        var winners = section(s,"winners"); for (String r : winners.getKeys(false)) e.winners.put(r,strings(winners,r).stream().map(UUID::fromString).toList());
        var results = section(s,"results"); for (String r : results.getKeys(false)) e.results.put(r,string(results,r));
        e.history.addAll(strings(s,"history")); if (e.history.size()>20) throw new IOException("History limit exceeded"); return e;
    }
    private static ConfigurationSection section(ConfigurationSection s, String key) throws IOException {
        var v=s.getConfigurationSection(key); if(v==null)throw new IOException("Missing section: "+key);return v;
    }
    private static long number(ConfigurationSection s,String key) { return ElectionsSettings.integer(s,key,0,Long.MAX_VALUE); }
    private static String string(ConfigurationSection s,String key) throws IOException { if(!(s.get(key) instanceof String v))throw new IOException("Expected string: "+key);return v; }
    private static List<String> strings(ConfigurationSection s,String key) throws IOException {
        if(!(s.get(key) instanceof List<?> list)||list.stream().anyMatch(v->!(v instanceof String)))throw new IOException("Expected string list: "+key);
        return s.getStringList(key);
    }
}
