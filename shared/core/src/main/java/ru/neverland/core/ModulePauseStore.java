package ru.neverland.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** File protocol 1: written before shutdown, read before any addon's repositories or tasks. */
public final class ModulePauseStore {
    public record Entry(boolean disabled, long pausedAt) {
        public Entry { if(pausedAt < 0 || disabled && pausedAt == 0) throw new IllegalArgumentException("Некорректная пауза модуля"); }
    }
    public record State(Set<String> requests, Map<String,Entry> modules) {
        public State { requests=Set.copyOf(requests); modules=Map.copyOf(modules); }
    }
    private ModulePauseStore() {}
    public static Path file(Path plugins) { return plugins.resolve("NeverLandTownyControl/modules.yml"); }
    public static State load(Path file) {
        if(!Files.exists(file)) return new State(Set.of(),Map.of());
        var y=SafeYaml.load(file); SafeYaml.keys(y,"schema","requests","modules");
        if(SafeYaml.intValue(y,"schema")!=1) throw new IllegalStateException("Неизвестная версия управления модулями");
        var entries=new LinkedHashMap<String,Entry>(); var root=SafeYaml.section(y,"modules");
        if(root!=null) for(String name:root.getKeys(false)) {
            validateName(name); var s=SafeYaml.section(root,name);
            if(s==null) throw new IllegalArgumentException("Повреждён модуль "+name);
            SafeYaml.keys(s,"disabled","paused-at");
            entries.put(name,new Entry(SafeYaml.booleanValue(s,"disabled"),SafeYaml.longValue(s,"paused-at")));
        }
        var requests=new HashSet<>(SafeYaml.strings(y,"requests")); requests.forEach(ModulePauseStore::validateName);
        AtomicFiles.loaded(file); return new State(requests,entries);
    }
    public static void save(Path file,State state)throws IOException {
        var y=new YamlConfiguration();y.set("schema",1);y.set("requests",state.requests().stream().sorted().toList());y.createSection("modules");
        state.modules().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{validateName(e.getKey());String p="modules."+e.getKey()+".";y.set(p+"disabled",e.getValue().disabled());y.set(p+"paused-at",e.getValue().pausedAt());});
        AtomicFiles.write(file,y::saveToString);
    }
    public static State request(State before,Set<String> requested,Set<String> disabled,long now) {
        var entries=new HashMap<>(before.modules());
        for(String name:disabled) { var old=entries.get(name); entries.put(name,new Entry(true,old!=null&&old.pausedAt()>0?old.pausedAt():now)); }
        entries.replaceAll((name,e)->new Entry(disabled.contains(name),e.pausedAt()));
        return new State(requested,entries);
    }
    public static void resumed(Path file,String name,long expected)throws IOException {
        State s=load(file);Entry entry=s.modules().get(name);
        if(entry==null||entry.disabled()||entry.pausedAt()!=expected) throw new IOException("Состояние паузы изменилось: "+name);
        var entries=new HashMap<>(s.modules()); entries.put(name,new Entry(false,0)); save(file,new State(s.requests(),entries));
    }
    public static void validateName(String name) { if(!name.matches("NeverLandTowny[A-Za-z]+")||name.equals("NeverLandTownyControl"))throw new IllegalArgumentException("Неизвестное имя аддона: "+name); }
}
