package ru.neverland.townyachievements.model;

import java.util.*;

/** Saved with city progress: editing the catalogue cannot rewrite an existing reward. */
public record Achievement(String id, String name, String description, String icon, Kind kind,
                          long target, List<String> projects, Reward reward, Track track, int tier, int minimumLevel) {
    public enum Kind { BALANCE, POPULATION, RAID_VICTORIES, ALL_BUILDINGS, FIRST_WONDER, BUILDING_SET }
    public enum Track {
        COMMON("Общие"), TRADE("Торговля"), INDUSTRY("Производство"), MILITARY("Оборона"), CULTURE("Культура");
        private final String label; Track(String label) { this.label = label; }
        public String label() { return label; }
    }
    /** Compatibility constructor for previously saved definitions and existing integrations. */
    public Achievement(String id, String name, String description, String icon, Kind kind, long target, List<String> projects, Reward reward) {
        this(id, name, description, icon, kind, target, projects, reward, Track.COMMON, legacyTier(id), kind == Kind.ALL_BUILDINGS ? 5 : 1);
    }
    public static int legacyTier(String id) { return Set.of("first_million", "population_1000", "raid_veterans", "all_buildings_v").contains(id) ? 3 : 1; }
    public String tierLabel() { return switch (tier) { case 1 -> "I"; case 2 -> "II"; default -> "III"; }; }
    public record Reward(String title, String cosmetic, String banner, double happiness) {
        public Reward {
            if (title == null || title.isBlank() || title.length() > 80 || title.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Некорректный титул");
            if (!Set.of("", "spark", "celebration", "guard", "architect", "wonder").contains(cosmetic)
                    || !Set.of("", "wealth", "city", "guard", "architect", "wonder").contains(banner)
                    || !Double.isFinite(happiness) || happiness < 0 || happiness > 10)
                throw new IllegalArgumentException("Некорректная награда");
        }
    }
    public Achievement {
        if (id == null || !id.matches("[a-z][a-z0-9_]{0,63}") || name == null || name.isBlank()
                || description == null || icon == null || kind == null || reward == null || track == null || tier < 1 || tier > 3 || minimumLevel < 1 || minimumLevel > 5 || target < 1 || target > 1_000_000_000_000L)
            throw new IllegalArgumentException("Некорректное достижение");
        projects = List.copyOf(projects);
        if (projects.size() > 500 || projects.size() != new HashSet<>(projects).size()
                || projects.stream().anyMatch(p -> !p.matches("[a-z][a-z0-9_]{0,63}")))
            throw new IllegalArgumentException("Некорректный список построек");
        if ((kind == Kind.ALL_BUILDINGS || kind == Kind.BUILDING_SET) && (projects.isEmpty() || target != projects.size())
                || kind == Kind.FIRST_WONDER && (projects.isEmpty() || target != 1))
            throw new IllegalArgumentException("Нужен непустой каталог построек");
        if (kind == Kind.ALL_BUILDINGS && minimumLevel != 5 || kind != Kind.ALL_BUILDINGS && kind != Kind.BUILDING_SET && minimumLevel != 1)
            throw new IllegalArgumentException("Уровень построек не соответствует виду достижения");
    }
}
