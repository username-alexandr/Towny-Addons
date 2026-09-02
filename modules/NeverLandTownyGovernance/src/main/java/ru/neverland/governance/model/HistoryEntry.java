package ru.neverland.governance.model;

import java.util.UUID;

public record HistoryEntry(long timestamp, String type, String description, UUID actorId, String actorName) { }
