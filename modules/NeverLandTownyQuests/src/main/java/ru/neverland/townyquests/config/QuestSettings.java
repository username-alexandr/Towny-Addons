package ru.neverland.townyquests.config;

import java.math.BigDecimal;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import ru.neverland.townyquests.model.Project;

public record QuestSettings(int interval, int resourceMaxAge, Map<String, Project> projects) {
    public QuestSettings {
        projects = Collections.unmodifiableMap(new LinkedHashMap<>(projects));
        if (interval < 1 || interval > 60 || resourceMaxAge < 60 || resourceMaxAge > 3600
                || projects.isEmpty() || projects.size() > 45) throw new IllegalArgumentException("Неверные настройки проектов");
        if (projects.values().stream().map(Project::unlock).distinct().count() != projects.size())
            throw new IllegalArgumentException("Результаты проектов должны быть уникальны");
        for (var p : projects.values()) visit(p.id(), projects, new HashSet<>(), new HashSet<>());
    }
    private static void visit(String id, Map<String, Project> projects, Set<String> path, Set<String> done) {
        if (done.contains(id)) return;
        if (!projects.containsKey(id) || !path.add(id)) throw new IllegalArgumentException("Отсутствующая или циклическая зависимость: " + id);
        projects.get(id).requires().forEach(child -> visit(child, projects, path, done));
        path.remove(id); done.add(id);
    }
    public static int integer(Object raw) {
        try { return new BigDecimal(String.valueOf(raw)).intValueExact(); }
        catch (Exception ex) { throw new IllegalArgumentException("Нужно целое число: " + raw); }
    }
    public static List<String> strings(Object raw) {
        if (!(raw instanceof List<?> list)) throw new IllegalArgumentException("Нужен список");
        var result = new ArrayList<String>();
        for (var value : list) { if (!(value instanceof String s)) throw new IllegalArgumentException("Нужна строка"); result.add(s); }
        return List.copyOf(result);
    }
    public static ConfigurationSection section(ConfigurationSection parent, String key) {
        var s = parent.getConfigurationSection(key);
        if (s == null) throw new IllegalArgumentException("Нет раздела " + key);
        return s;
    }
    public static Project project(String id, ConfigurationSection s) {
        var stages = new ArrayList<Project.Stage>(); var root = section(s, "stages");
        for (String key : root.getKeys(false)) {
            var stage = section(root, key);
            stages.add(new Project.Stage(key, stage.getString("name"), Project.Kind.valueOf(stage.getString("type", "")),
                    stage.getString("building", ""), integer(stage.get("target")), integer(stage.get("hold-seconds", 0))));
        }
        return new Project(id, s.getString("name"), s.getString("icon"), strings(s.get("requires")),
                s.getString("unlock"), s.getString("reward"), stages);
    }
    public static QuestSettings load(ConfigurationSection config, ConfigurationSection quests) {
        Map<String, Project> projects = new LinkedHashMap<>(); var root = section(quests, "projects");
        for (String id : root.getKeys(false)) projects.put(id, project(id, section(root, id)));
        return new QuestSettings(integer(config.get("simulation.interval-seconds", 5)),
                integer(config.get("simulation.resource-max-age-seconds", 180)), projects);
    }
    public static void writeProject(ConfigurationSection s, Project p) {
        s.set("name", p.name()); s.set("icon", p.icon()); s.set("requires", p.requires());
        s.set("unlock", p.unlock()); s.set("reward", p.reward()); var stages = s.createSection("stages");
        for (var stage : p.stages()) {
            var v = stages.createSection(stage.id()); v.set("name", stage.name()); v.set("type", stage.kind().name());
            v.set("building", stage.building()); v.set("target", stage.target()); v.set("hold-seconds", stage.holdSeconds());
        }
    }
}
