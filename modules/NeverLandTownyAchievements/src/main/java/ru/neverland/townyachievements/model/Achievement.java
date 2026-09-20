package ru.neverland.townyachievements.model;

import java.util.*;

/** Saved with city progress: editing the catalogue cannot rewrite an existing reward. */
public record Achievement(String id, String name, String description, String icon, Kind kind,
                          long target, List<String> projects, Reward reward) {
    public enum Kind { BALANCE, POPULATION, RAID_VICTORIES, ALL_BUILDINGS, FIRST_WONDER }
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
                || description == null || icon == null || kind == null || reward == null || target < 1 || target > 1_000_000_000_000L)
            throw new IllegalArgumentException("Некорректное достижение");
        projects = List.copyOf(projects);
        if (projects.size() > 500 || projects.size() != new HashSet<>(projects).size()
                || projects.stream().anyMatch(p -> !p.matches("[a-z][a-z0-9_]{0,63}")))
            throw new IllegalArgumentException("Некорректный список построек");
        if (kind == Kind.ALL_BUILDINGS && (projects.isEmpty() || target != projects.size())
                || kind == Kind.FIRST_WONDER && (projects.isEmpty() || target != 1))
            throw new IllegalArgumentException("Нужен непустой каталог построек");
    }
}
