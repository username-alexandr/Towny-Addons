package ru.neverland.mintevents.model;

import java.util.UUID;

public final class ActiveEvent {
    private final UUID townId;
    private final String eventId;
    private final long startedAt;
    private final long endsAt;
    private final int goal;
    private int progress;
    private double protection;
    private long lastRaidWave;

    public ActiveEvent(UUID townId, String eventId, long startedAt, long endsAt,
                       int progress, int goal, double protection, long lastRaidWave) {
        this.townId = townId;
        this.eventId = eventId;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
        this.progress = Math.max(0, progress);
        this.goal = Math.max(1, goal);
        this.protection = Math.max(0, Math.min(1, protection));
        this.lastRaidWave = lastRaidWave;
    }

    public UUID townId() { return townId; }
    public String eventId() { return eventId; }
    public long startedAt() { return startedAt; }
    public long endsAt() { return endsAt; }
    public int progress() { return progress; }
    public int goal() { return goal; }
    public double protection() { return protection; }
    public long lastRaidWave() { return lastRaidWave; }
    public void protection(double value) { protection = Math.max(0, Math.min(1, value)); }
    public void lastRaidWave(long value) { lastRaidWave = value; }
    public int addProgress(int points) {
        progress = Math.min(goal, progress + Math.max(0, points));
        return progress;
    }
    public boolean completed() { return progress >= goal; }
    public double progressRatio() { return Math.min(1.0, (double) progress / goal); }
    public long secondsLeft(long now) { return Math.max(0, (endsAt - now + 999) / 1000); }
}
