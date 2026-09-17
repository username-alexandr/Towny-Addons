package ru.neverland.core;

import org.bukkit.configuration.ConfigurationSection;

/** Administrative time changes never create a new activity or financial operation. */
public record ActivityTimer(long deadline, long pausedAt, long duration) {
    public ActivityTimer {
        if (deadline < 0 || pausedAt < 0 || duration <= 0 || pausedAt > deadline)
            throw new IllegalArgumentException("Повреждён таймер задачи");
    }
    public static ActivityTimer running(long start, long end) {
        return new ActivityTimer(end, 0, Math.max(1, Math.subtractExact(end, start)));
    }
    public boolean paused() { return pausedAt != 0; }
    public long now(long wallTime) { return paused() ? pausedAt : wallTime; }
    public long remaining(long wallTime) { return Math.max(0, deadline - now(wallTime)); }
    public ActivityTimer edit(String action, long minutes, long wallTime) {
        if (wallTime <= 0) throw new IllegalArgumentException("Некорректное время сервера");
        return switch (action) {
            case "pause" -> {
                if (paused()) throw new IllegalArgumentException("Задача уже на паузе");
                if (deadline <= wallTime) throw new IllegalArgumentException("Срок истёк: сначала extend или restart");
                yield new ActivityTimer(deadline, wallTime, duration);
            }
            case "resume" -> {
                if (!paused()) throw new IllegalArgumentException("Задача не на паузе");
                if (wallTime < pausedAt) throw new IllegalArgumentException("Часы сервера идут назад");
                yield new ActivityTimer(Math.addExact(deadline, wallTime - pausedAt), 0, duration);
            }
            case "restart" -> new ActivityTimer(Math.addExact(now(wallTime), duration), pausedAt, duration);
            case "extend" -> {
                if (minutes < 1 || minutes > 10080) throw new IllegalArgumentException("Продление: 1–10080 минут");
                yield new ActivityTimer(Math.addExact(deadline, Math.multiplyExact(minutes, 60000)), pausedAt, duration);
            }
            default -> throw new IllegalArgumentException("Неизвестное действие с таймером: " + action);
        };
    }
    public static ActivityTimer read(ConfigurationSection section, String prefix, ActivityTimer initial) {
        return new ActivityTimer(initial.deadline(), SafeYaml.longValue(section, prefix + "admin-paused-at", 0),
                SafeYaml.longValue(section, prefix + "admin-duration", initial.duration()));
    }
    public void write(ConfigurationSection section, String prefix) {
        section.set(prefix + "admin-paused-at", pausedAt);
        section.set(prefix + "admin-duration", duration);
    }
    public String describe(long now) {
        long seconds = (remaining(now) + 999) / 1000;
        return (paused() ? "Пауза" : "Работает") + "; осталось " + seconds / 60 + " мин. " + seconds % 60 + " сек.";
    }
}
