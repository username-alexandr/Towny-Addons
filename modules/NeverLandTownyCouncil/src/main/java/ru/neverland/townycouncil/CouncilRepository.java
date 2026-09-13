package ru.neverland.townycouncil;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.AtomicFiles;

/** Publish in memory only after the complete registry and audit have been committed. */
public final class CouncilRepository {
    public record Audit(UUID town, long at, String actor, String detail) {
        public Audit {
            Objects.requireNonNull(town);
            if (at < 0 || actor == null || actor.isBlank() || detail == null || detail.isBlank())
                throw new IllegalArgumentException("Повреждён журнал совета");
        }
    }
    private final Path file;
    private Map<String, Appointment> records = Map.of();
    private List<Audit> history = List.of();
    private boolean writable;
    public CouncilRepository(Path file) { this.file = file; }
    public boolean writable() { return writable && AtomicFiles.writable(file); }
    public Map<String, Appointment> all() { return records; }
    public List<Audit> history() { return history; }
    public void load() throws Exception {
        writable = false;
        var next = new LinkedHashMap<String, Appointment>(); var audit = new ArrayList<Audit>();
        if (Files.exists(file)) {
            var y = new YamlConfiguration(); y.load(file.toFile());
            if (number(y, "schema") != 1 || !y.isConfigurationSection("appointments") || !y.isConfigurationSection("history"))
                throw new IOException("Неизвестная схема совета");
            for (String key : y.getConfigurationSection("appointments").getKeys(false)) {
                var s = section(y, "appointments." + key);
                var a = new Appointment(UUID.fromString(string(s, "town")), MinisterRole.parse(string(s, "role")),
                        UUID.fromString(string(s, "resident")), UUID.fromString(string(s, "mayor")), number(s, "at"), string(s, "actor"));
                if (!a.key().equals(key)) throw new IOException("Несовпадение ключа назначения");
                next.put(key, a);
            }
            for (String key : y.getConfigurationSection("history").getKeys(false)) {
                var s = section(y, "history." + key);
                audit.add(new Audit(UUID.fromString(string(s, "town")), number(s, "at"), string(s, "actor"), string(s, "detail")));
            }
            if (audit.size() > 500) throw new IOException("Слишком большой журнал совета");
        }
        validate(next); records = Map.copyOf(next); history = List.copyOf(audit); AtomicFiles.loaded(file); writable = true;
    }
    public void commit(Map<String, Appointment> next, List<Audit> additions) throws IOException {
        if (!writable()) throw new IOException("Реестр совета остановлен после ошибки; нужен перезапуск после восстановления файла");
        validate(next);
        var audit = new ArrayList<>(history); audit.addAll(additions);
        if (audit.size() > 500) audit = new ArrayList<>(audit.subList(audit.size() - 500, audit.size()));
        var y = new YamlConfiguration(); y.set("schema", 1); y.createSection("appointments"); y.createSection("history");
        new TreeMap<>(next).forEach((key, a) -> {
            String p = "appointments." + key + ".";
            y.set(p + "town", a.town().toString()); y.set(p + "role", a.role().id()); y.set(p + "resident", a.resident().toString());
            y.set(p + "mayor", a.mayor().toString()); y.set(p + "at", a.appointedAt()); y.set(p + "actor", a.actor());
        });
        for (int i = 0; i < audit.size(); i++) {
            var a = audit.get(i); String p = "history." + i + ".";
            y.set(p + "town", a.town().toString()); y.set(p + "at", a.at()); y.set(p + "actor", a.actor()); y.set(p + "detail", a.detail());
        }
        try { AtomicFiles.write(file, y::saveToString); records = Map.copyOf(next); history = List.copyOf(audit); }
        catch (IOException ex) { writable = false; throw ex; }
    }
    private static void validate(Map<String, Appointment> records) {
        var holders = new HashSet<String>();
        records.forEach((key, a) -> {
            if (!key.equals(a.key()) || !holders.add(a.town() + "/" + a.resident()))
                throw new IllegalArgumentException("Дублирование министра или повреждённый ключ");
        });
    }
    private static ConfigurationSection section(ConfigurationSection s, String key) throws IOException {
        var value = s.getConfigurationSection(key); if (value == null) throw new IOException("Повреждён раздел " + key); return value;
    }
    private static String string(ConfigurationSection s, String key) throws IOException {
        if (!(s.get(key) instanceof String text)) throw new IOException("Ожидалась строка " + key); return text;
    }
    private static long number(ConfigurationSection s, String key) throws IOException {
        Object v = s.get(key); if (!(v instanceof Integer || v instanceof Long) || ((Number) v).longValue() < 0)
            throw new IOException("Ожидалось целое неотрицательное число " + key); return ((Number) v).longValue();
    }
}
