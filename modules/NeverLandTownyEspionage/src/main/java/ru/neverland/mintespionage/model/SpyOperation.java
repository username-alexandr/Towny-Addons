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
    public UUID id(){ synchronized (ru.neverland.core.SuiteState.lock()) {return id;} } public UUID attackerTownId(){ synchronized (ru.neverland.core.SuiteState.lock()) {return attackerTownId;} } public String attackerName(){ synchronized (ru.neverland.core.SuiteState.lock()) {return attackerName;} }
    public UUID targetTownId(){ synchronized (ru.neverland.core.SuiteState.lock()) {return targetTownId;} } public String targetName(){ synchronized (ru.neverland.core.SuiteState.lock()) {return targetName;} } public UUID actorId(){ synchronized (ru.neverland.core.SuiteState.lock()) {return actorId;} }
    public String actorName(){ synchronized (ru.neverland.core.SuiteState.lock()) {return actorName;} } public String type(){ synchronized (ru.neverland.core.SuiteState.lock()) {return type;} } public long startedAt(){ synchronized (ru.neverland.core.SuiteState.lock()) {return startedAt;} }
    public long completesAt(){ synchronized (ru.neverland.core.SuiteState.lock()) {return timer.deadline();} } public double successChance(){ synchronized (ru.neverland.core.SuiteState.lock()) {return successChance;} }
    public double detectionChance(){ synchronized (ru.neverland.core.SuiteState.lock()) {return detectionChance;} } public double cost(){ synchronized (ru.neverland.core.SuiteState.lock()) {return cost;} } public OperationStatus status(){ synchronized (ru.neverland.core.SuiteState.lock()) {return status;} }
    public boolean detected(){ synchronized (ru.neverland.core.SuiteState.lock()) {return detected;} } public void finish(OperationStatus value, boolean wasDetected){ synchronized (ru.neverland.core.SuiteState.lock()) {status=value; detected=wasDetected;} }
    public String shortId(){ synchronized (ru.neverland.core.SuiteState.lock()) {return id.toString().substring(0,8);} }
    public ru.neverland.core.ActivityTimer timer(){ synchronized (ru.neverland.core.SuiteState.lock()) {return timer;} }
    public void timer(ru.neverland.core.ActivityTimer value){ synchronized (ru.neverland.core.SuiteState.lock()) {timer=java.util.Objects.requireNonNull(value);} }
}
