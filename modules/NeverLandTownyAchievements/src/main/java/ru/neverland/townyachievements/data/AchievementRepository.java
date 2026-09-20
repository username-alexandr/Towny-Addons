package ru.neverland.townyachievements.data;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;
import ru.neverland.townyachievements.config.AchievementSettings;
import ru.neverland.townyachievements.model.*;

public final class AchievementRepository {
    public record Cosmetic(UUID town, String achievement) {
        public Cosmetic { Objects.requireNonNull(town); if (achievement == null || !achievement.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("Некорректная косметика"); }
    }
    private final Path file; private boolean ready;
    private Map<UUID, CityAchievements> towns = Map.of();
    private Map<UUID, Cosmetic> cosmetics = Map.of();
    public AchievementRepository(Path file) { this.file = file; }
    public Map<UUID, CityAchievements> towns() { return towns; }
    public Map<UUID, Cosmetic> cosmetics() { return cosmetics; }
    public CityAchievements get(UUID town) { return towns.getOrDefault(town, CityAchievements.empty()); }
    public void load() throws Exception {
        ready = false;
        if (!Files.exists(file)) { towns = Map.of(); cosmetics = Map.of(); AtomicFiles.loaded(file); ready = true; return; }
        var y = new YamlConfiguration(); y.load(file.toFile());
        if (SafeYaml.integer(y, "schema") != 1) throw new IOException("Неизвестная схема достижений");
        var root = AchievementSettings.section(y, "towns"); Map<UUID, CityAchievements> next = new HashMap<>();
        for (String raw : root.getKeys(false)) {
            var city = AchievementSettings.section(root, raw); var entries = AchievementSettings.section(city, "progress");
            Map<String, Progress> progress = new TreeMap<>();
            for (String id : entries.getKeys(false)) {
                var p = AchievementSettings.section(entries, id);
                progress.put(id, new Progress(AchievementSettings.read(id, AchievementSettings.section(p, "definition")), SafeYaml.integer(p, "best"), SafeYaml.integer(p, "earned-at")));
            }
            next.put(UUID.fromString(raw), new CityAchievements(SafeYaml.booleanValue(city, "paused", false), SafeYaml.text(city, "title"), progress));
        }
        Map<UUID, Cosmetic> effects = new HashMap<>(); var rootEffects = AchievementSettings.section(y, "cosmetics");
        for (String player : rootEffects.getKeys(false)) {
            var c = AchievementSettings.section(rootEffects, player);
            effects.put(UUID.fromString(player), new Cosmetic(UUID.fromString(SafeYaml.text(c, "town")), SafeYaml.text(c, "achievement")));
        }
        validateCosmetics(next, effects);
        towns = Map.copyOf(next); cosmetics = Map.copyOf(effects); AtomicFiles.loaded(file); ready = true;
    }
    private static void validateCosmetics(Map<UUID, CityAchievements> cities, Map<UUID, Cosmetic> effects) {
        for (var c : effects.values()) {
            var p = cities.getOrDefault(c.town(), CityAchievements.empty()).progress().get(c.achievement());
            if (p == null || !p.earned() || p.definition().reward().cosmetic().isEmpty()) throw new IllegalArgumentException("Косметика ещё не открыта");
        }
    }
    public void put(UUID town, CityAchievements city) throws IOException {
        if (city.equals(towns.get(town))) return;
        var next = new HashMap<>(towns); next.put(Objects.requireNonNull(town), city); save(next, cosmetics);
    }
    public void cosmetic(UUID player, Cosmetic cosmetic) throws IOException {
        var next = new HashMap<>(cosmetics); if (cosmetic == null) next.remove(player); else next.put(player, cosmetic);
        if (!next.equals(cosmetics)) save(towns, next);
    }
    private void save(Map<UUID, CityAchievements> cities, Map<UUID, Cosmetic> effects) throws IOException {
        if (!ready || !AtomicFiles.writable(file)) throw new IOException("Хранилище достижений заблокировано");
        validateCosmetics(cities, effects);
        var y = new YamlConfiguration(); y.set("schema", 1); var root = y.createSection("towns");
        for (var entry : cities.entrySet()) {
            var s = root.createSection(entry.getKey().toString()); var city = entry.getValue();
            s.set("paused", city.paused()); s.set("title", city.title()); var progress = s.createSection("progress");
            city.progress().forEach((id, p) -> {
                var row = progress.createSection(id); row.set("best", p.best()); row.set("earned-at", p.earnedAt());
                AchievementSettings.write(row.createSection("definition"), p.definition());
            });
        }
        var c = y.createSection("cosmetics"); effects.forEach((player, effect) -> {
            var s = c.createSection(player.toString()); s.set("town", effect.town().toString()); s.set("achievement", effect.achievement());
        });
        AtomicFiles.write(file, y::saveToString); towns = Map.copyOf(cities); cosmetics = Map.copyOf(effects);
    }
}
