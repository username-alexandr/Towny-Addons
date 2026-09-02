package ru.neverland.reputation.api;

import ru.neverland.reputation.model.ChangeStatus;

public record ReputationChangeResult(ChangeStatus status, int oldScore, int newScore, int requestedDelta, int appliedDelta) { }
