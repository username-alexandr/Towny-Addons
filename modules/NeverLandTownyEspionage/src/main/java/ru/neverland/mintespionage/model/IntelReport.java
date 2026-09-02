package ru.neverland.mintespionage.model;

import java.util.List;
import java.util.UUID;

public final class IntelReport {
    private final UUID id, ownerTownId, targetTownId; private final String targetName, type;
    private final long createdAt, expiresAt; private final List<String> lines; private boolean read;
    public IntelReport(UUID id, UUID ownerTownId, UUID targetTownId, String targetName, String type, long createdAt,
                       long expiresAt, List<String> lines, boolean read) {
        this.id=id; this.ownerTownId=ownerTownId; this.targetTownId=targetTownId; this.targetName=targetName; this.type=type;
        this.createdAt=createdAt; this.expiresAt=expiresAt; this.lines=List.copyOf(lines); this.read=read;
    }
    public UUID id(){return id;} public UUID ownerTownId(){return ownerTownId;} public UUID targetTownId(){return targetTownId;}
    public String targetName(){return targetName;} public String type(){return type;} public long createdAt(){return createdAt;}
    public long expiresAt(){return expiresAt;} public List<String> lines(){return lines;} public boolean read(){return read;}
    public void markRead(){read=true;} public boolean expired(long now){return expiresAt>0 && expiresAt<=now;}
}
