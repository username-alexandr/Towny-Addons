package ru.neverland.reputation.service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;
import ru.neverland.reputation.model.*;
import static ru.neverland.reputation.model.ReputationLedger.*;

/** Immutable snapshots become visible only after force + atomic replacement succeeds. */
public final class ProfileRepository {
    private final Path path;
    private volatile State state = State.empty();
    private boolean ready;
    public ProfileRepository(Path path) { this.path = path; }
    public boolean healthy() { return ready && AtomicFiles.writable(path); }
    public State state() { return state; }
    public void load(Collection<ReputationRecord> legacy) throws IOException {
        ready = false;
        try {
            State next;
            boolean migrate = !Files.exists(path);
            if (migrate) next = migrate(legacy);
            else {
                var y = SafeYaml.load(path); SafeYaml.keys(y, "schema", "profiles", "receipts", "daily");
                if (SafeYaml.integer(y, "schema") != 1 || !y.isList("profiles") || !y.isList("receipts") || !y.isList("daily")) throw new IllegalArgumentException("Неизвестная схема профилей");
                var profiles = new LinkedHashMap<Subject, Profile>();
                for (var row : SafeYaml.maps(y, "profiles")) {
                    var s = section(row); SafeYaml.keys(s, "subject", "diplomatic", "trade", "military", "history");
                    var history = new ArrayList<Entry>();
                    for (var raw : SafeYaml.maps(s, "history")) { var e = section(raw); SafeYaml.keys(e,"at","aspect","delta","score","rule","context"); history.add(new Entry(SafeYaml.integer(e,"at"), ReputationAspect.valueOf(SafeYaml.text(e,"aspect")), SafeYaml.intValue(e,"delta"), SafeYaml.intValue(e,"score"), SafeYaml.text(e,"rule"), SafeYaml.text(e,"context"))); }
                    if (history.size() > 60) throw new IllegalArgumentException("Переполнена история профиля");
                    var profile = new Profile(Math.toIntExact(SafeYaml.integer(s,"diplomatic")), Math.toIntExact(SafeYaml.integer(s,"trade")), Math.toIntExact(SafeYaml.integer(s,"military")), history);
                    if (profiles.put(Subject.parse(SafeYaml.text(s,"subject")), profile) != null) throw new IllegalArgumentException("Повтор профиля");
                }
                var receipts = new LinkedHashMap<String, Receipt>();
                for (var row : SafeYaml.maps(y,"receipts")) {
                    var s = section(row); SafeYaml.keys(s,"id","scope","subject","rule","at","context","aspect","requested","applied");
                    var o = new ReputationOutcome(SafeYaml.text(s,"id"), SafeYaml.text(s,"scope"), UUID.fromString(SafeYaml.text(s,"subject")), SafeYaml.text(s,"rule"), SafeYaml.integer(s,"at"), SafeYaml.text(s,"context"));
                    int applied = Math.toIntExact(SafeYaml.integer(s,"applied")); if (Math.abs((long)applied) > 2000) throw new IllegalArgumentException("Повреждена квитанция");
                    var receipt = new Receipt(o, ReputationAspect.valueOf(SafeYaml.text(s,"aspect")), Math.toIntExact(SafeYaml.integer(s,"requested")), applied);
                    if (!profiles.containsKey(new Subject(ReputationScope.valueOf(o.scope()), o.subject())) || receipts.put(o.id(),receipt) != null) throw new IllegalArgumentException("Повтор/потеря профиля квитанции");
                }
                var daily = new LinkedHashMap<String,Integer>();
                for (var row : SafeYaml.maps(y,"daily")) { var s = section(row); SafeYaml.keys(s,"key","used"); String key = SafeYaml.text(s,"key"); int used = Math.toIntExact(SafeYaml.integer(s,"used")); if (used < 0 || daily.put(key,used) != null) throw new IllegalArgumentException("Повреждён дневной лимит"); }
                next = new State(profiles, receipts, daily);
            }
            AtomicFiles.loaded(path); ready = true;
            if (migrate) commit(next); else state = next;
        } catch (Exception ex) { ready = false; throw new IOException("profiles.yml повреждён; изменения репутации и новые комиссии остановлены", ex); }
    }
    static State migrate(Collection<ReputationRecord> legacy) {
        var totals = new HashMap<Subject, long[]>();
        for (var relation : legacy) {
            var key = relation.key();
            var subjects = new ArrayList<Subject>(); subjects.add(new Subject(key.scope(), key.second()));
            if (key.scope() != ReputationScope.PLAYER) subjects.add(new Subject(key.scope(), key.first()));
            for (var subject : subjects) { long[] total = totals.computeIfAbsent(subject, ignored -> new long[2]); total[0] += relation.score(); total[1]++; }
        }
        var profiles = new LinkedHashMap<Subject,Profile>();
        totals.forEach((subject,total) -> profiles.put(subject, new Profile((int)Math.max(MIN, Math.min(MAX, Math.round((double)total[0] / total[1]))), 0, 0, List.of())));
        return new State(profiles, Map.of(), Map.of());
    }
    public void commit(State next) throws IOException {
        if (!healthy()) throw new IOException("Хранилище профилей остановлено");
        try {
            var y = new YamlConfiguration(); y.set("schema",1);
            y.set("profiles",next.profiles().entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().key())).map(e -> Map.of("subject",e.getKey().key(), "diplomatic",e.getValue().diplomatic(), "trade",e.getValue().trade(), "military",e.getValue().military(), "history",e.getValue().history().stream().map(h -> Map.of("at",h.at(),"aspect",h.aspect().name(),"delta",h.delta(),"score",h.score(),"rule",h.rule(),"context",h.context())).toList())).toList());
            y.set("receipts",next.receipts().values().stream().sorted(Comparator.comparing(r -> r.outcome().id())).map(r -> { var o = r.outcome(); return Map.of("id",o.id(),"scope",o.scope(),"subject",o.subject().toString(),"rule",o.rule(),"at",o.at(),"context",o.context(),"aspect",r.aspect().name(),"requested",r.requested(),"applied",r.applied()); }).toList());
            y.set("daily",new TreeMap<>(next.daily()).entrySet().stream().map(e -> Map.of("key",e.getKey(),"used",e.getValue())).toList());
            AtomicFiles.write(path,y::saveToString); state = next;
        } catch (IOException | RuntimeException ex) { ready = false; throw new IOException("Профили не сохранены; дальнейшие изменения остановлены",ex); }
    }
    private static ConfigurationSection section(Map<?,?> row) { var s = new MemoryConfiguration(); row.forEach((k,v) -> { if (!(k instanceof String key)) throw new IllegalArgumentException("Неверный ключ YAML"); s.set(key,v); }); return s; }
}
