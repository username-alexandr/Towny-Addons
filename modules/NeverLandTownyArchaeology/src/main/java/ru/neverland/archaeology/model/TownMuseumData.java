package ru.neverland.archaeology.model;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TownMuseumData {
    private final UUID townId; private String townName; private int points;
    private final Map<String, Integer> available = new LinkedHashMap<>(); private final Map<String, Integer> donated = new LinkedHashMap<>();
    private final Map<UUID, Integer> contributors = new LinkedHashMap<>(); private final Set<String> completedCollections = new LinkedHashSet<>();
    public TownMuseumData(UUID townId, String townName) { this.townId = townId; this.townName = townName; }
    public UUID townId() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return townId; } } public String townName() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return townName; } } public void townName(String value) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { if (value != null && !value.isBlank()) townName = value; } }
    public int points() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return points; } } public void points(int value) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { points = Math.max(0, value); } }
    public Map<String, Integer> available() { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { return available; } } public Map<String, Integer> donated() { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return donated; } } public Map<UUID, Integer> contributors() { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { return contributors; } } public Set<String> completedCollections() { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) { return completedCollections; } }
    public int available(String artifact) { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) { return available.getOrDefault(artifact, 0); } } public int donated(String artifact) { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) { return donated.getOrDefault(artifact, 0); } }
    public void add(String artifact, int amount, int artifactPoints, UUID contributor) { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) { available.merge(artifact, amount, Integer::sum); donated.merge(artifact, amount, Integer::sum); points += artifactPoints * amount; if (contributor != null) contributors.merge(contributor, amount, Integer::sum); } }
    public boolean consume(Map<String, Integer> requirements) { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) { for (Map.Entry<String, Integer> entry : requirements.entrySet()) if (available(entry.getKey()) < entry.getValue()) return false; for (Map.Entry<String, Integer> entry : requirements.entrySet()) { int left = available(entry.getKey()) - entry.getValue(); if (left == 0) available.remove(entry.getKey()); else available.put(entry.getKey(), left); } return true; } }
}
