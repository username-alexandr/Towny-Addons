package ru.neverland.reputation.model;

import java.util.UUID;

public record ReputationHistory(
        long timestamp,
        int oldScore,
        int newScore,
        int delta,
        String source,
        String reason,
        UUID actorId,
        String actorName
) { }
