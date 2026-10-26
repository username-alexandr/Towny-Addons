package ru.neverland.mintexpeditions.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.*;

public final class ActiveExpedition {
    private final UUID id, leaderId, worldId;
    private final String definitionId, worldName;
    private final BlockPos campAnchor, siteAnchor;
    private final long startedAt;
    private ru.neverland.core.ActivityTimer timer;
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Set<BlockPos> objectives = new LinkedHashSet<>(), completed = new LinkedHashSet<>();
    private final Map<BlockPos, SiteSnapshot> snapshots = new LinkedHashMap<>();
    private final Set<UUID> spawnedMobs = new LinkedHashSet<>();
    private int kills;
    private ExpeditionStatus status = ExpeditionStatus.ACTIVE;

    public ActiveExpedition(UUID id, UUID leaderId, String definitionId, UUID worldId, String worldName,
                            BlockPos campAnchor, BlockPos siteAnchor, long startedAt, long expiresAt) {
        this.id=id; this.leaderId=leaderId; this.definitionId=definitionId; this.worldId=worldId;
        this.worldName=worldName; this.campAnchor=campAnchor; this.siteAnchor=siteAnchor;
        this.startedAt=startedAt; this.timer=ru.neverland.core.ActivityTimer.running(startedAt,expiresAt);
    }
    public UUID id(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return id;} } public UUID leaderId(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return leaderId;} } public String definitionId(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return definitionId;} }
    public UUID worldId(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return worldId;} } public String worldName(){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {return worldName;} } public BlockPos campAnchor(){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {return campAnchor;} }
    public BlockPos siteAnchor(){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {return siteAnchor;} } public long startedAt(){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {return startedAt;} } public long expiresAt(){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {return timer.deadline();} }
    public Set<UUID> participants(){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {return participants;} } public Set<BlockPos> objectives(){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {return objectives;} }
    public Set<BlockPos> completed(){ try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {return completed;} } public Map<BlockPos,SiteSnapshot> snapshots(){ try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {return snapshots;} }
    public Set<UUID> spawnedMobs(){ try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {return spawnedMobs;} } public int kills(){ try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {return kills;} } public void kills(int v){ try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {kills=Math.max(0,v);} }
    public ExpeditionStatus status(){ try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {return status;} } public void status(ExpeditionStatus v){ try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {status=v;} }
    public World world(){ try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) {World w=Bukkit.getWorld(worldId); return w==null?Bukkit.getWorld(worldName):w;} }
    public Location siteLocation(){ try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) {World w=world(); return w==null?null:siteAnchor.location(w).add(.5,1,.5);} }
    public Location campLocation(){ try (var suiteGuard21 = ru.neverland.core.SuiteState.guard()) {World w=world(); return w==null?null:campAnchor.location(w).add(.5,1,.5);} }
    public int objectiveProgress(){ try (var suiteGuard22 = ru.neverland.core.SuiteState.guard()) {return completed.size();} }
    public ru.neverland.core.ActivityTimer timer(){ try (var suiteGuard23 = ru.neverland.core.SuiteState.guard()) {return timer;} }
    public void timer(ru.neverland.core.ActivityTimer value){ try (var suiteGuard24 = ru.neverland.core.SuiteState.guard()) {timer=java.util.Objects.requireNonNull(value);} }
}
