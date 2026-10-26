package ru.neverland.townyachievements.model;

import java.util.*;

public record CityAchievements(boolean paused, String title, Map<String, Progress> progress) {
    public CityAchievements {
        progress = Map.copyOf(progress);
        progress.forEach((id, p) -> { if (!id.equals(p.definition().id())) throw new IllegalArgumentException("ID достижения не совпадает"); });
        if (title == null || !title.isEmpty() && (!progress.containsKey(title) || !progress.get(title).earned()))
            throw new IllegalArgumentException("Титул ещё не открыт");
    }
    public static CityAchievements empty() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return new CityAchievements(false, "", Map.of()); } }
    public CityAchievements put(Progress p) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        var next = new TreeMap<>(progress); next.put(p.definition().id(), p);
        return new CityAchievements(paused, title, next);
    } }
    public CityAchievements paused(boolean value) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return new CityAchievements(value, title, progress); } }
    public CityAchievements title(String value) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return new CityAchievements(paused, value, progress); } }
    public double happiness() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return Math.min(10, progress.values().stream().filter(Progress::earned).mapToDouble(p -> p.definition().reward().happiness()).sum()); } }
}
