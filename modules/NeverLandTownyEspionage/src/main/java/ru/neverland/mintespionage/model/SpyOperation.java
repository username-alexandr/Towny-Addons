package ru.neverland.mintespionage.model;

import java.util.UUID;

public final class SpyOperation {
    private final UUID id, attackerTownId, targetTownId, actorId;
    private final String attackerName, targetName, actorName, type;
    private final long startedAt;
    private ru.neverland.core.ActivityTimer timer;
    private final double successChance, detectionChance, cost;
    private OperationStatus status; private boolean detected;
    public SpyOperation(UUID id, UUID attackerTownId, String attackerName, UUID targetTownId, String targetName, UUID actorId,
                        String actorName, String type, long startedAt, long completesAt, double successChance,
                        double detectionChance, double cost, OperationStatus status, boolean detected) {
        this.id=id; this.attackerTownId=attackerTownId; this.attackerName=attackerName; this.targetTownId=targetTownId;
        this.targetName=targetName; this.actorId=actorId; this.actorName=actorName; this.type=type; this.startedAt=startedAt;
        this.timer=ru.neverland.core.ActivityTimer.running(startedAt,completesAt); this.successChance=successChance; this.detectionChance=detectionChance; this.cost=cost;
        this.status=status; this.detected=detected;
    }
    public UUID id(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return id;} } public UUID attackerTownId(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return attackerTownId;} } public String attackerName(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return attackerName;} }
    public UUID targetTownId(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return targetTownId;} } public String targetName(){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {return targetName;} } public UUID actorId(){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {return actorId;} }
    public String actorName(){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {return actorName;} } public String type(){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {return type;} } public long startedAt(){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {return startedAt;} }
    public long completesAt(){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {return timer.deadline();} } public double successChance(){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {return successChance;} }
    public double detectionChance(){ try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {return detectionChance;} } public double cost(){ try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {return cost;} } public OperationStatus status(){ try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {return status;} }
    public boolean detected(){ try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {return detected;} } public void finish(OperationStatus value, boolean wasDetected){ try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {status=value; detected=wasDetected;} }
    public String shortId(){ try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {return id.toString().substring(0,8);} }
    public ru.neverland.core.ActivityTimer timer(){ try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {return timer;} }
    public void timer(ru.neverland.core.ActivityTimer value){ try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) {timer=java.util.Objects.requireNonNull(value);} }
}
