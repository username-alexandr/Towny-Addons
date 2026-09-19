package ru.neverland.townyquests.data;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.AtomicFiles;
import ru.neverland.townyquests.config.QuestSettings;
import ru.neverland.townyquests.model.CityProject;

public final class QuestRepository {
    private final Path file;
    private boolean writable;
    private Map<UUID, Map<String, CityProject>> towns = Map.of();
    public QuestRepository(Path file) { this.file = file; }
    public Map<UUID, Map<String, CityProject>> towns() { return towns; }
    public Map<String, CityProject> get(UUID town) { return towns.getOrDefault(town, Map.of()); }
    public void load() throws Exception {
        writable = false;
        if (!Files.exists(file)) { towns = Map.of(); AtomicFiles.loaded(file); writable = true; return; }
        var y = new YamlConfiguration(); y.load(file.toFile());
        if (QuestSettings.integer(y.get("schema")) != 1) throw new IOException("Неизвестная схема quests-data.yml");
        var root = QuestSettings.section(y, "towns"); Map<UUID, Map<String, CityProject>> next = new HashMap<>();
        for (String town : root.getKeys(false)) {
            var city = QuestSettings.section(root, town); Map<String, CityProject> runs = new LinkedHashMap<>();
            for (String id : city.getKeys(false)) {
                var s = QuestSettings.section(city, id);
                runs.put(id, new CityProject(UUID.fromString(s.getString("run")),
                        QuestSettings.project(id, QuestSettings.section(s, "definition")),
                        QuestSettings.integer(s.get("stage")), QuestSettings.integer(s.get("held-seconds")),
                        CityProject.Status.valueOf(s.getString("status"))));
            }
            validate(runs); next.put(UUID.fromString(town), Map.copyOf(runs));
        }
        towns = Map.copyOf(next); AtomicFiles.loaded(file); writable = true;
    }
    private static void validate(Map<String, CityProject> runs) {
        if (runs.values().stream().filter(r -> r.status() == CityProject.Status.ACTIVE || r.status() == CityProject.Status.PAUSED).count() > 1)
            throw new IllegalArgumentException("В городе уже есть текущий проект");
        runs.forEach((id, run) -> { if (!id.equals(run.definition().id())) throw new IllegalArgumentException("ID проекта не совпадает"); });
    }
    public void put(UUID town, CityProject run) throws IOException {
        if (!writable || !AtomicFiles.writable(file)) throw new IOException("База проектов недоступна для записи");
        if (run.equals(get(town).get(run.definition().id()))) return;
        var city = new LinkedHashMap<>(get(town)); city.put(run.definition().id(), run); validate(city);
        var next = new HashMap<>(towns); next.put(town, Map.copyOf(city));
        var y = new YamlConfiguration(); y.set("schema", 1); var root = y.createSection("towns");
        for (var entry : next.entrySet()) {
            var target = root.createSection(entry.getKey().toString());
            for (var r : entry.getValue().values()) {
                var s = target.createSection(r.definition().id()); s.set("run", r.run().toString());
                s.set("stage", r.stage()); s.set("held-seconds", r.heldSeconds()); s.set("status", r.status().name());
                QuestSettings.writeProject(s.createSection("definition"), r.definition());
            }
        }
        AtomicFiles.write(file, y::saveToString); towns = Map.copyOf(next);
    }
}
