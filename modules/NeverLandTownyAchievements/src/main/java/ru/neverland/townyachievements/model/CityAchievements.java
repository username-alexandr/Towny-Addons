package ru.neverland.townyachievements.model;

import java.util.*;

public record CityAchievements(boolean paused, String title, Map<String, Progress> progress) {
    public CityAchievements {
        progress = Map.copyOf(progress);
        progress.forEach((id, p) -> { if (!id.equals(p.definition().id())) throw new IllegalArgumentException("ID достижения не совпадает"); });
        if (title == null || !title.isEmpty() && (!progress.containsKey(title) || !progress.get(title).earned()))
            throw new IllegalArgumentException("Титул ещё не открыт");
    }
    public static CityAchievements empty() { synchronized (ru.neverland.core.SuiteState.lock()) { return new CityAchievements(false, "", Map.of()); } }
    public CityAchievements put(Progress p) { synchronized (ru.neverland.core.SuiteState.lock()) {
        var next = new TreeMap<>(progress); next.put(p.definition().id(), p);
        return new CityAchievements(paused, title, next);
    } }
    public CityAchievements paused(boolean value) { synchronized (ru.neverland.core.SuiteState.lock()) { return new CityAchievements(value, title, progress); } }
    public CityAchievements title(String value) { synchronized (ru.neverland.core.SuiteState.lock()) { return new CityAchievements(paused, value, progress); } }
    public double happiness() { synchronized (ru.neverland.core.SuiteState.lock()) { return Math.min(10, progress.values().stream().filter(Progress::earned).mapToDouble(p -> p.definition().reward().happiness()).sum()); } }
}
