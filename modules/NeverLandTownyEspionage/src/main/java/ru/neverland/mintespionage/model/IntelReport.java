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
    public UUID id(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return id;} } public UUID ownerTownId(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return ownerTownId;} } public UUID targetTownId(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return targetTownId;} }
    public String targetName(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return targetName;} } public String type(){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {return type;} } public long createdAt(){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {return createdAt;} }
    public long expiresAt(){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {return expiresAt;} } public List<String> lines(){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {return lines;} } public boolean read(){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {return read;} }
    public void markRead(){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {read=true;} } public boolean expired(long now){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {return expiresAt>0 && expiresAt<=now;} }
}
