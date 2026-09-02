package ru.neverland.archaeology.model;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerJournal {
    private final UUID playerId; private String playerName; private int totalFound; private final Map<String, Integer> discoveries = new LinkedHashMap<>(); private final Set<String> serials = new LinkedHashSet<>();
    public PlayerJournal(UUID playerId, String playerName) { this.playerId = playerId; this.playerName = playerName; }
    public UUID playerId() { return playerId; } public String playerName() { return playerName; } public void playerName(String value) { if (value != null && !value.isBlank()) playerName = value; }
    public int totalFound() { return totalFound; } public void totalFound(int value) { totalFound = Math.max(0, value); } public Map<String, Integer> discoveries() { return discoveries; } public Set<String> serials() { return serials; }
    public boolean discover(String artifact, String serial) { if (!serials.add(serial)) return false; boolean first = !discoveries.containsKey(artifact); discoveries.merge(artifact, 1, Integer::sum); totalFound++; return first; }
}
