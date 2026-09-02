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
    public UUID townId() { return townId; } public String townName() { return townName; } public void townName(String value) { if (value != null && !value.isBlank()) townName = value; }
    public int points() { return points; } public void points(int value) { points = Math.max(0, value); }
    public Map<String, Integer> available() { return available; } public Map<String, Integer> donated() { return donated; } public Map<UUID, Integer> contributors() { return contributors; } public Set<String> completedCollections() { return completedCollections; }
    public int available(String artifact) { return available.getOrDefault(artifact, 0); } public int donated(String artifact) { return donated.getOrDefault(artifact, 0); }
    public void add(String artifact, int amount, int artifactPoints, UUID contributor) { available.merge(artifact, amount, Integer::sum); donated.merge(artifact, amount, Integer::sum); points += artifactPoints * amount; if (contributor != null) contributors.merge(contributor, amount, Integer::sum); }
    public boolean consume(Map<String, Integer> requirements) { for (Map.Entry<String, Integer> entry : requirements.entrySet()) if (available(entry.getKey()) < entry.getValue()) return false; for (Map.Entry<String, Integer> entry : requirements.entrySet()) { int left = available(entry.getKey()) - entry.getValue(); if (left == 0) available.remove(entry.getKey()); else available.put(entry.getKey(), left); } return true; }
}
