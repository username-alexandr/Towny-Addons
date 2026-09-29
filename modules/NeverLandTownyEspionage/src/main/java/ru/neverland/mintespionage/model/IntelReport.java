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
    public UUID id(){ synchronized (ru.neverland.core.SuiteState.lock()) {return id;} } public UUID ownerTownId(){ synchronized (ru.neverland.core.SuiteState.lock()) {return ownerTownId;} } public UUID targetTownId(){ synchronized (ru.neverland.core.SuiteState.lock()) {return targetTownId;} }
    public String targetName(){ synchronized (ru.neverland.core.SuiteState.lock()) {return targetName;} } public String type(){ synchronized (ru.neverland.core.SuiteState.lock()) {return type;} } public long createdAt(){ synchronized (ru.neverland.core.SuiteState.lock()) {return createdAt;} }
    public long expiresAt(){ synchronized (ru.neverland.core.SuiteState.lock()) {return expiresAt;} } public List<String> lines(){ synchronized (ru.neverland.core.SuiteState.lock()) {return lines;} } public boolean read(){ synchronized (ru.neverland.core.SuiteState.lock()) {return read;} }
    public void markRead(){ synchronized (ru.neverland.core.SuiteState.lock()) {read=true;} } public boolean expired(long now){ synchronized (ru.neverland.core.SuiteState.lock()) {return expiresAt>0 && expiresAt<=now;} }
}
