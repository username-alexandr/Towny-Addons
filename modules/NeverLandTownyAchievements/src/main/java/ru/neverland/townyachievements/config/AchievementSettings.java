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
    public static ConfigurationSection section(ConfigurationSection root, String key) {
        var s = SafeYaml.section(root, key); if (s == null) throw new IllegalArgumentException("Отсутствует раздел " + key); return s;
    }
    public static AchievementSettings load(ConfigurationSection config, ConfigurationSection catalogue) {
        var root = section(catalogue, "achievements"); var result = new LinkedHashMap<String, Achievement>();
        for (String id : root.getKeys(false)) result.put(id, read(id, section(root, id)));
        return new AchievementSettings(SafeYaml.intValue(config, "check-seconds", 30), SafeYaml.booleanValue(config, "cosmetics-enabled", true), result);
    }
    public static Achievement read(String id, ConfigurationSection s) {
        var r = section(s, "reward");
        var kind = Achievement.Kind.valueOf(SafeYaml.text(s, "kind"));
        List<String> projects = s.contains("projects") ? SafeYaml.strings(s, "projects") : List.of();
        long target = kind == Achievement.Kind.ALL_BUILDINGS ? projects.size() : SafeYaml.integer(s, "target");
        return new Achievement(id, SafeYaml.text(s, "name"), SafeYaml.text(s, "description"), SafeYaml.text(s, "icon"), kind, target, projects,
                new Achievement.Reward(SafeYaml.text(r, "title"), SafeYaml.stringValue(r, "cosmetic", ""), SafeYaml.stringValue(r, "banner", ""), SafeYaml.doubleValue(r, "happiness", 0)));
    }
    public static void write(ConfigurationSection s, Achievement a) {
        s.set("name", a.name()); s.set("description", a.description()); s.set("icon", a.icon()); s.set("kind", a.kind().name());
        s.set("target", a.target()); s.set("projects", a.projects());
        var r = s.createSection("reward"); r.set("title", a.reward().title()); r.set("cosmetic", a.reward().cosmetic());
        r.set("banner", a.reward().banner()); r.set("happiness", a.reward().happiness());
    }
}
