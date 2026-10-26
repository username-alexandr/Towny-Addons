package ru.neverland.archaeology.model;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerJournal {
    private final UUID playerId; private String playerName; private int totalFound; private final Map<String, Integer> discoveries = new LinkedHashMap<>(); private final Set<String> serials = new LinkedHashSet<>();
    public PlayerJournal(UUID playerId, String playerName) { this.playerId = playerId; this.playerName = playerName; }
    public UUID playerId() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return playerId; } } public String playerName() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return playerName; } } public void playerName(String value) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { if (value != null && !value.isBlank()) playerName = value; } }
    public int totalFound() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return totalFound; } } public void totalFound(int value) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { totalFound = Math.max(0, value); } } public Map<String, Integer> discoveries() { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { return discoveries; } } public Set<String> serials() { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return serials; } }
    public boolean discover(String artifact, String serial) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { if (!serials.add(serial)) return false; boolean first = !discoveries.containsKey(artifact); discoveries.merge(artifact, 1, Integer::sum); totalFound++; return first; } }
}
