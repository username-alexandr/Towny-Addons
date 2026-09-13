package ru.neverland.townycitizens;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.AtomicFiles;
import ru.neverland.townycitizens.model.CitizenshipRecord;
import ru.neverland.townycitizens.model.CitizenshipStatus;

public final class CitizensRepository {
    private final Path file;
    private Map<String, CitizenshipRecord> records = Map.of();
    private boolean writable;
    public CitizensRepository(Path file) { this.file = file; }
    public boolean writable() { return writable && AtomicFiles.writable(file); }
    public Map<String, CitizenshipRecord> all() { return records; }
    public CitizenshipRecord get(UUID town, UUID resident) { return records.get(town + "/" + resident); }
    public void load() throws Exception {
        writable = false;
        Map<String, CitizenshipRecord> next = new LinkedHashMap<>();
        if (Files.exists(file)) {
            var y = new YamlConfiguration(); y.load(file.toFile());
            if (number(y, "schema") != 1 || !y.isConfigurationSection("records")) throw new IOException("Неизвестная схема гражданства");
            for (String key : y.getConfigurationSection("records").getKeys(false)) {
                var s = y.getConfigurationSection("records." + key);
                if (s == null) throw new IOException("Повреждена запись " + key);
                var record = new CitizenshipRecord(UUID.fromString(string(s, "town")), UUID.fromString(string(s, "resident")),
                        CitizenshipStatus.valueOf(string(s, "status")), number(s, "expires-at"), number(s, "changed-at"),
                        string(s, "actor"), string(s, "reason"));
                if (!record.key().equals(key)) throw new IOException("Несовпадение идентификатора гражданства");
                next.put(key, record);
            }
        }
        records = Map.copyOf(next); AtomicFiles.loaded(file); writable = true;
    }
    public void put(CitizenshipRecord record) throws IOException {
        if (!writable()) throw new IOException("Реестр гражданства остановлен после ошибки записи");
        var next = new LinkedHashMap<>(records); next.put(record.key(), record);
        var y = new YamlConfiguration(); y.set("schema", 1); y.createSection("records");
        next.forEach((key, r) -> {
            String p = "records." + key + ".";
            y.set(p + "town", r.town().toString()); y.set(p + "resident", r.resident().toString());
            y.set(p + "status", r.status().name()); y.set(p + "expires-at", r.expiresAt());
            y.set(p + "changed-at", r.changedAt()); y.set(p + "actor", r.actor()); y.set(p + "reason", r.reason());
        });
        try { AtomicFiles.write(file, y::saveToString); records = Map.copyOf(next); }
        catch (IOException ex) { writable = false; throw ex; }
    }
    static long number(ConfigurationSection s, String key) throws IOException {
        Object v = s.get(key);
        if (!(v instanceof Integer || v instanceof Long) || ((Number) v).longValue() < 0)
            throw new IOException("Ожидалось целое неотрицательное число: " + key);
        return ((Number) v).longValue();
    }
    static String string(ConfigurationSection s, String key) throws IOException {
        if (!(s.get(key) instanceof String text)) throw new IOException("Ожидалась строка: " + key);
        return text;
    }
}
