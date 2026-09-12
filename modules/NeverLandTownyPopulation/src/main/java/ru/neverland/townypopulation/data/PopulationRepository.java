package ru.neverland.townypopulation.data;

import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townypopulation.model.PopulationState;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Strict load and atomic replace: an unreadable database is never silently reset. */
public final class PopulationRepository {
    private final Path file;
    private final Map<UUID, PopulationState> states = new HashMap<>();
    private boolean dirty,ready;
    private void gate(){if(!ready||!ru.neverland.core.AtomicFiles.writable(file))throw new IllegalStateException("Хранилище населения недоступно");}
    public PopulationRepository(Path file) { this.file = file; }
    public void load() throws Exception {ready=false;
        Map<UUID, PopulationState> loaded = new HashMap<>();
        if (Files.exists(file)) {
            YamlConfiguration yaml = new YamlConfiguration(); yaml.load(file.toFile());
            if (yaml.getInt("schema",0) != 1) throw new IOException("Неизвестная версия population-data.yml");
            var towns = yaml.getConfigurationSection("towns");
            if (towns == null) throw new IOException("Нет раздела towns в population-data.yml");
            for (String id : towns.getKeys(false)) {
                String key = "towns."+id+".";
                for (String field : List.of("population","remainder","last-change","last-cycle"))
                    if (!(yaml.get(key+field) instanceof Number)) throw new IOException("Повреждённое поле "+key+field);
                double population = yaml.getDouble(key+"population");
                if (population != Math.rint(population) || population < 0 || population > 1000000)
                    throw new IOException("Некорректное население: "+id);
                loaded.put(UUID.fromString(id), new PopulationState((int)population,
                        yaml.getDouble(key+"remainder"), yaml.getInt(key+"last-change"), yaml.getLong(key+"last-cycle")));
            }
        }
        states.clear(); states.putAll(loaded); dirty = false;ru.neverland.core.AtomicFiles.loaded(file);ready=true;
    }
    public PopulationState get(UUID id) { return states.get(id); }
    public Set<UUID> ids() { return Set.copyOf(states.keySet()); }
    public void put(UUID id, PopulationState state) {gate(); if (!state.equals(states.put(id,state))) dirty = true; }
    public void retain(Set<UUID> ids) {gate(); if (states.keySet().retainAll(ids)) dirty = true; }
    public void rebase(long now) { for (UUID id : ids()) put(id,states.get(id).rebase(now)); }
    public void save() throws IOException {gate();
        if (!dirty) return;
        Files.createDirectories(file.toAbsolutePath().getParent());
        YamlConfiguration yaml = new YamlConfiguration(); yaml.set("schema",1); yaml.createSection("towns");
        for (var entry : states.entrySet()) {
            String key = "towns."+entry.getKey()+"."; var s = entry.getValue();
            yaml.set(key+"population",s.population()); yaml.set(key+"remainder",s.remainder());
            yaml.set(key+"last-change",s.lastChange()); yaml.set(key+"last-cycle",s.lastCycle());
        }
        ru.neverland.core.AtomicFiles.write(file,yaml::saveToString);dirty=false;
    }
}
