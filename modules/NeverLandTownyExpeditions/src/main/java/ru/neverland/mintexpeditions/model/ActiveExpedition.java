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
    public UUID id(){ synchronized (ru.neverland.core.SuiteState.lock()) {return id;} } public UUID leaderId(){ synchronized (ru.neverland.core.SuiteState.lock()) {return leaderId;} } public String definitionId(){ synchronized (ru.neverland.core.SuiteState.lock()) {return definitionId;} }
    public UUID worldId(){ synchronized (ru.neverland.core.SuiteState.lock()) {return worldId;} } public String worldName(){ synchronized (ru.neverland.core.SuiteState.lock()) {return worldName;} } public BlockPos campAnchor(){ synchronized (ru.neverland.core.SuiteState.lock()) {return campAnchor;} }
    public BlockPos siteAnchor(){ synchronized (ru.neverland.core.SuiteState.lock()) {return siteAnchor;} } public long startedAt(){ synchronized (ru.neverland.core.SuiteState.lock()) {return startedAt;} } public long expiresAt(){ synchronized (ru.neverland.core.SuiteState.lock()) {return timer.deadline();} }
    public Set<UUID> participants(){ synchronized (ru.neverland.core.SuiteState.lock()) {return participants;} } public Set<BlockPos> objectives(){ synchronized (ru.neverland.core.SuiteState.lock()) {return objectives;} }
    public Set<BlockPos> completed(){ synchronized (ru.neverland.core.SuiteState.lock()) {return completed;} } public Map<BlockPos,SiteSnapshot> snapshots(){ synchronized (ru.neverland.core.SuiteState.lock()) {return snapshots;} }
    public Set<UUID> spawnedMobs(){ synchronized (ru.neverland.core.SuiteState.lock()) {return spawnedMobs;} } public int kills(){ synchronized (ru.neverland.core.SuiteState.lock()) {return kills;} } public void kills(int v){ synchronized (ru.neverland.core.SuiteState.lock()) {kills=Math.max(0,v);} }
    public ExpeditionStatus status(){ synchronized (ru.neverland.core.SuiteState.lock()) {return status;} } public void status(ExpeditionStatus v){ synchronized (ru.neverland.core.SuiteState.lock()) {status=v;} }
    public World world(){ synchronized (ru.neverland.core.SuiteState.lock()) {World w=Bukkit.getWorld(worldId); return w==null?Bukkit.getWorld(worldName):w;} }
    public Location siteLocation(){ synchronized (ru.neverland.core.SuiteState.lock()) {World w=world(); return w==null?null:siteAnchor.location(w).add(.5,1,.5);} }
    public Location campLocation(){ synchronized (ru.neverland.core.SuiteState.lock()) {World w=world(); return w==null?null:campAnchor.location(w).add(.5,1,.5);} }
    public int objectiveProgress(){ synchronized (ru.neverland.core.SuiteState.lock()) {return completed.size();} }
    public ru.neverland.core.ActivityTimer timer(){ synchronized (ru.neverland.core.SuiteState.lock()) {return timer;} }
    public void timer(ru.neverland.core.ActivityTimer value){ synchronized (ru.neverland.core.SuiteState.lock()) {timer=java.util.Objects.requireNonNull(value);} }
}
