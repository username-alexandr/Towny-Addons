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

    public UUID townId() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return townId; } }
    public String eventId() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return eventId; } }
    public long startedAt() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return startedAt; } }
    public long endsAt() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return endsAt; } }
    public int progress() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return progress; } }
    public int goal() { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { return goal; } }
    public double protection() { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return protection; } }
    public long lastRaidWave() { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { return lastRaidWave; } }
    public void protection(double value) { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) { protection = Math.max(0, Math.min(1, value)); } }
    public void lastRaidWave(long value) { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) { lastRaidWave = value; } }
    public int addProgress(int points) { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {
        progress = (int) Math.min(raid == null ? goal : Integer.MAX_VALUE, (long) progress + Math.max(0, points));
        return progress;
    } }
    public RaidState raid() { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) { return raid; } }
    public void raid(RaidState state) { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) { raid = state; } }
    public boolean paused() { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) { return pausedAt > 0; } }
    public long pausedAt() { try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) { return pausedAt; } }
    public void pausedAt(long value) { try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) { if(value < 0) throw new IllegalArgumentException("Отрицательная дата паузы"); pausedAt = value; } }
    public ActiveEvent reschedule(long deadline, long pause) { try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {
        if(deadline <= 0 || pause < 0) throw new IllegalArgumentException("Некорректное время события");
        ActiveEvent copy = new ActiveEvent(townId, eventId, startedAt, deadline, progress, goal, protection, lastRaidWave);
        copy.raid = raid; copy.pausedAt = pause; return copy;
    } }
    public boolean completed() { try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) { return raid == null ? progress >= goal : raid.finished(); } }
    public double progressRatio() { try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) { return raid == null ? Math.min(1.0, (double) progress / goal) : raid.ratio(); } }
    public long secondsLeft(long now) { try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) { return Math.max(0, (endsAt - (paused() ? pausedAt : now) + 999) / 1000); } }
}
