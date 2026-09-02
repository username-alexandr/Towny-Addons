package ru.neverland.mintespionage.model;

import java.util.UUID;

public final class SpyOperation {
    private final UUID id, attackerTownId, targetTownId, actorId;
    private final String attackerName, targetName, actorName, type;
    private final long startedAt, completesAt;
    private final double successChance, detectionChance, cost;
    private OperationStatus status; private boolean detected;
    public SpyOperation(UUID id, UUID attackerTownId, String attackerName, UUID targetTownId, String targetName, UUID actorId,
                        String actorName, String type, long startedAt, long completesAt, double successChance,
                        double detectionChance, double cost, OperationStatus status, boolean detected) {
        this.id=id; this.attackerTownId=attackerTownId; this.attackerName=attackerName; this.targetTownId=targetTownId;
        this.targetName=targetName; this.actorId=actorId; this.actorName=actorName; this.type=type; this.startedAt=startedAt;
        this.completesAt=completesAt; this.successChance=successChance; this.detectionChance=detectionChance; this.cost=cost;
        this.status=status; this.detected=detected;
    }
    public UUID id(){return id;} public UUID attackerTownId(){return attackerTownId;} public String attackerName(){return attackerName;}
    public UUID targetTownId(){return targetTownId;} public String targetName(){return targetName;} public UUID actorId(){return actorId;}
    public String actorName(){return actorName;} public String type(){return type;} public long startedAt(){return startedAt;}
    public long completesAt(){return completesAt;} public double successChance(){return successChance;}
    public double detectionChance(){return detectionChance;} public double cost(){return cost;} public OperationStatus status(){return status;}
    public boolean detected(){return detected;} public void finish(OperationStatus value, boolean wasDetected){status=value; detected=wasDetected;}
    public String shortId(){return id.toString().substring(0,8);}
}
