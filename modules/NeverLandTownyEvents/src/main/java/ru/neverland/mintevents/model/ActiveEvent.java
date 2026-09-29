package ru.neverland.mintevents.model;

import java.util.UUID;

public final class ActiveEvent {
    private final UUID townId;
    private final String eventId;
    private final long startedAt;
    private final long endsAt;
    private final int goal;
    private long pausedAt;
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

    public UUID townId() { synchronized (ru.neverland.core.SuiteState.lock()) { return townId; } }
    public String eventId() { synchronized (ru.neverland.core.SuiteState.lock()) { return eventId; } }
    public long startedAt() { synchronized (ru.neverland.core.SuiteState.lock()) { return startedAt; } }
    public long endsAt() { synchronized (ru.neverland.core.SuiteState.lock()) { return endsAt; } }
    public int progress() { synchronized (ru.neverland.core.SuiteState.lock()) { return progress; } }
    public int goal() { synchronized (ru.neverland.core.SuiteState.lock()) { return goal; } }
    public double protection() { synchronized (ru.neverland.core.SuiteState.lock()) { return protection; } }
    public long lastRaidWave() { synchronized (ru.neverland.core.SuiteState.lock()) { return lastRaidWave; } }
    public void protection(double value) { synchronized (ru.neverland.core.SuiteState.lock()) { protection = Math.max(0, Math.min(1, value)); } }
    public void lastRaidWave(long value) { synchronized (ru.neverland.core.SuiteState.lock()) { lastRaidWave = value; } }
    public int addProgress(int points) { synchronized (ru.neverland.core.SuiteState.lock()) {
        progress = (int) Math.min(raid == null ? goal : Integer.MAX_VALUE, (long) progress + Math.max(0, points));
        return progress;
    } }
    public RaidState raid() { synchronized (ru.neverland.core.SuiteState.lock()) { return raid; } }
    public void raid(RaidState state) { synchronized (ru.neverland.core.SuiteState.lock()) { raid = state; } }
    public boolean paused() { synchronized (ru.neverland.core.SuiteState.lock()) { return pausedAt > 0; } }
    public long pausedAt() { synchronized (ru.neverland.core.SuiteState.lock()) { return pausedAt; } }
    public void pausedAt(long value) { synchronized (ru.neverland.core.SuiteState.lock()) { if(value < 0) throw new IllegalArgumentException("Отрицательная дата паузы"); pausedAt = value; } }
    public ActiveEvent reschedule(long deadline, long pause) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if(deadline <= 0 || pause < 0) throw new IllegalArgumentException("Некорректное время события");
        ActiveEvent copy = new ActiveEvent(townId, eventId, startedAt, deadline, progress, goal, protection, lastRaidWave);
        copy.raid = raid; copy.pausedAt = pause; return copy;
    } }
    public boolean completed() { synchronized (ru.neverland.core.SuiteState.lock()) { return raid == null ? progress >= goal : raid.finished(); } }
    public double progressRatio() { synchronized (ru.neverland.core.SuiteState.lock()) { return raid == null ? Math.min(1.0, (double) progress / goal) : raid.ratio(); } }
    public long secondsLeft(long now) { synchronized (ru.neverland.core.SuiteState.lock()) { return Math.max(0, (endsAt - (paused() ? pausedAt : now) + 999) / 1000); } }
}
