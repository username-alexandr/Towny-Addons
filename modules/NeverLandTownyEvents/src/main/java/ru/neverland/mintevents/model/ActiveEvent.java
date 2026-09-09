package ru.neverland.mintevents.model;

import java.util.UUID;

public final class ActiveEvent {
    private final UUID townId;
    private final String eventId;
    private final long startedAt;
    private final long endsAt;
    private final int goal;
    private int progress;
    private RaidState raid;
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
        progress = (int) Math.min(raid == null ? goal : Integer.MAX_VALUE, (long) progress + Math.max(0, points));
        return progress;
    }
    public RaidState raid() { return raid; }
    public void raid(RaidState state) { raid = state; }
    public boolean completed() { return raid == null ? progress >= goal : raid.finished(); }
    public double progressRatio() { return raid == null ? Math.min(1.0, (double) progress / goal) : raid.ratio(); }
    public long secondsLeft(long now) { return Math.max(0, (endsAt - now + 999) / 1000); }
}
