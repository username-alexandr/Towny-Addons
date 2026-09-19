package ru.neverland.townyquests.model;

import java.util.*;

/** A run keeps this immutable definition even after administrators edit quests.yml. */
public record Project(String id, String name, String icon, List<String> requires,
                      String unlock, String reward, List<Stage> stages) {
    public Project {
        identifier(id); identifier(unlock);
        if (name == null || name.isBlank() || reward == null || reward.isBlank()
                || icon == null || icon.isBlank()) throw new IllegalArgumentException("Нужны название, значок и результат проекта");
        requires = List.copyOf(requires); stages = List.copyOf(stages);
        requires.forEach(Project::identifier);
        if (requires.contains(id) || new HashSet<>(requires).size() != requires.size()
                || stages.isEmpty() || stages.size() > 20
                || stages.stream().map(Stage::id).distinct().count() != stages.size())
            throw new IllegalArgumentException("Неверные зависимости или этапы проекта");
    }
    public static void identifier(String value) {
        if (value == null || !value.matches("[a-z0-9_]{1,48}")) throw new IllegalArgumentException("Неверный идентификатор: " + value);
    }
    public enum Kind { BUILDING, WATER_DISTRICTS }
    public record Stage(String id, String name, Kind kind, String building, int target, int holdSeconds) {
        public Stage {
            identifier(id);
            if (name == null || name.isBlank() || kind == null || target < 1 || target > 4096
                    || holdSeconds < 0 || holdSeconds > 604800) throw new IllegalArgumentException("Неверный этап проекта");
            if (kind == Kind.BUILDING) { identifier(building); if (target > 5) throw new IllegalArgumentException("Уровень здания: 1..5"); }
            else if (!"".equals(building)) throw new IllegalArgumentException("У района не задаётся здание");
        }
    }
}
