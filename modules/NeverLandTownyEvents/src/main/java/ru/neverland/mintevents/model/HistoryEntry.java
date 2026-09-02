package ru.neverland.mintevents.model;

public record HistoryEntry(String eventId, long startedAt, long endedAt, int progress, int goal, boolean success) {}
