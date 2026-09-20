package ru.neverland.townyenvironment.data;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;
import ru.neverland.townyenvironment.config.EnvironmentSettings;
import ru.neverland.townyenvironment.model.EnvironmentState;

public final class EnvironmentRepository {
    private final Path file; private boolean ready; private Map<UUID, EnvironmentState> towns = Map.of();
    public EnvironmentRepository(Path file) { this.file = file; }
    public Map<UUID, EnvironmentState> towns() { return towns; }
    public EnvironmentState get(UUID town) { return towns.getOrDefault(town, EnvironmentState.clean()); }
    public void load() throws Exception {
        ready = false;
        if (!Files.exists(file)) { towns = Map.of(); AtomicFiles.loaded(file); ready = true; return; }
        var y = new YamlConfiguration(); y.load(file.toFile());
        if (SafeYaml.integer(y, "schema") != 1) throw new IOException("Неизвестная схема экологии");
        var root = EnvironmentSettings.section(y, "towns"); Map<UUID, EnvironmentState> next = new HashMap<>();
        for (String raw : root.getKeys(false)) {
            var row = EnvironmentSettings.section(root, raw);
            if (!row.contains("pollution") || !row.contains("paused")) throw new IOException("Неполная запись экологии");
            next.put(UUID.fromString(raw), new EnvironmentState(SafeYaml.doubleValue(row, "pollution"), SafeYaml.integer(row, "cycles"), SafeYaml.booleanValue(row, "paused")));
        }
        towns = Map.copyOf(next); AtomicFiles.loaded(file); ready = true;
    }
    public void put(UUID town, EnvironmentState state) throws IOException {
        var next = new HashMap<>(towns); next.put(Objects.requireNonNull(town), state); replace(next);
    }
    public void replace(Map<UUID, EnvironmentState> next) throws IOException {
        if (!ready || !AtomicFiles.writable(file)) throw new IOException("Хранилище экологии заблокировано");
        next = Map.copyOf(next); if (next.equals(towns)) return;
        var y = new YamlConfiguration(); y.set("schema", 1); var root = y.createSection("towns");
        next.forEach((id, state) -> { var s = root.createSection(id.toString()); s.set("pollution", state.pollution()); s.set("cycles", state.cycles()); s.set("paused", state.paused()); });
        AtomicFiles.write(file, y::saveToString); towns = next;
    }
}
