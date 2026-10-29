package ru.neverland.townyachievements.config;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import ru.neverland.core.SafeYaml;
import ru.neverland.townyachievements.model.Achievement;

public record AchievementSettings(int interval, boolean cosmetics, Map<String, Achievement> definitions) {
    public AchievementSettings {
        if (interval < 1 || interval > 3600 || definitions.isEmpty() || definitions.size() > 200) throw new IllegalArgumentException("Некорректные настройки достижений");
        definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }
    /** Add complete new IDs only. Never repair or override a user's existing definition field by field. */
    public static AchievementSettings loadWithAdditions(ConfigurationSection config, ConfigurationSection catalogue, ConfigurationSection bundled) {
        var configured = load(config, catalogue);
        if (!SafeYaml.booleanValue(config, "include-new-default-goals", true)) return configured;
        var combined = new LinkedHashMap<>(configured.definitions());
        var originals = Set.of("first_million", "population_1000", "raid_veterans", "all_buildings_v", "first_wonder");
        load(config, bundled).definitions().forEach((id, goal) -> { if (!originals.contains(id)) combined.putIfAbsent(id, goal); });
        return new AchievementSettings(configured.interval(), configured.cosmetics(), combined);
    }
    public static ConfigurationSection section(ConfigurationSection root, String key) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        var s = SafeYaml.section(root, key); if (s == null) throw new IllegalArgumentException("Отсутствует раздел " + key); return s;
    } }
    public static AchievementSettings load(ConfigurationSection config, ConfigurationSection catalogue) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        var root = section(catalogue, "achievements"); var result = new LinkedHashMap<String, Achievement>();
        for (String id : root.getKeys(false)) {
            try { result.put(id, read(id, section(root, id))); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("achievements." + id + ": " + error.getMessage(), error); }
        }
        return new AchievementSettings(SafeYaml.intValue(config, "check-seconds", 30), SafeYaml.booleanValue(config, "cosmetics-enabled", true), result);
    } }
    public static Achievement read(String id, ConfigurationSection s) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        var r = section(s, "reward");
        var kind = Achievement.Kind.valueOf(SafeYaml.text(s, "kind"));
        List<String> projects = s.contains("projects") ? SafeYaml.strings(s, "projects") : List.of();
        long target = (kind == Achievement.Kind.ALL_BUILDINGS || kind == Achievement.Kind.BUILDING_SET) ? projects.size() : SafeYaml.integer(s, "target");
        return new Achievement(id, SafeYaml.text(s, "name"), SafeYaml.text(s, "description"), SafeYaml.text(s, "icon"), kind, target, projects,
                new Achievement.Reward(SafeYaml.text(r, "title"), SafeYaml.stringValue(r, "cosmetic", ""), SafeYaml.stringValue(r, "banner", ""), SafeYaml.doubleValue(r, "happiness", 0)),
                Achievement.Track.valueOf(SafeYaml.stringValue(s, "track", "COMMON")), SafeYaml.intValue(s, "tier", Achievement.legacyTier(id)),
                SafeYaml.intValue(s, "minimum-level", kind == Achievement.Kind.ALL_BUILDINGS ? 5 : 1));
    } }
    public static void write(ConfigurationSection s, Achievement a) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        s.set("name", a.name()); s.set("description", a.description()); s.set("icon", a.icon()); s.set("kind", a.kind().name());
        s.set("target", a.target()); s.set("projects", a.projects());
        s.set("track", a.track().name()); s.set("tier", a.tier()); s.set("minimum-level", a.minimumLevel());
        var r = s.createSection("reward"); r.set("title", a.reward().title()); r.set("cosmetic", a.reward().cosmetic());
        r.set("banner", a.reward().banner()); r.set("happiness", a.reward().happiness());
    } }
}
