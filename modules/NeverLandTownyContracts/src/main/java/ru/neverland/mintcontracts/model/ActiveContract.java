package ru.neverland.mintcontracts.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ActiveContract {
    private final UUID id;
    private final UUID townId;
    private final String templateId;
    private final long createdAt;
    private final long expiresAt;
    private final int goal;
    private final double escrow;
    private final Map<UUID, Integer> contributions = new LinkedHashMap<>();
    private int progress;

    public ActiveContract(UUID id, UUID townId, String templateId, long createdAt, long expiresAt,
                          int progress, int goal, double escrow, Map<UUID, Integer> contributions) {
        this.id = id; this.townId = townId; this.templateId = templateId; this.createdAt = createdAt;
        this.expiresAt = expiresAt; this.progress = Math.max(0, progress); this.goal = Math.max(1, goal);
        this.escrow = Math.max(0, escrow); this.contributions.putAll(contributions);
    }
    public UUID id() { return id; }
    public UUID townId() { return townId; }
    public String templateId() { return templateId; }
    public long createdAt() { return createdAt; }
    public long expiresAt() { return expiresAt; }
    public int progress() { return progress; }
    public int goal() { return goal; }
    public double escrow() { return escrow; }
    public Map<UUID, Integer> contributions() { return Map.copyOf(contributions); }
    public int add(UUID playerId, int amount) {
        int accepted = Math.min(Math.max(0, amount), goal - progress);
        if (accepted <= 0) return 0;
        progress += accepted;
        contributions.merge(playerId, accepted, Integer::sum);
        return accepted;
    }
    public boolean completed() { return progress >= goal; }
    public double ratio() { return Math.min(1, (double) progress / goal); }
    public String shortId() { return id.toString().substring(0, 8); }
}
