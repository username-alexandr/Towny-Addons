package ru.neverland.mintexpeditions.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.*;

public final class ActiveExpedition {
    private final UUID id, leaderId, worldId;
    private final String definitionId, worldName;
    private final BlockPos campAnchor, siteAnchor;
    private final long startedAt, expiresAt;
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
        this.startedAt=startedAt; this.expiresAt=expiresAt;
    }
    public UUID id(){return id;} public UUID leaderId(){return leaderId;} public String definitionId(){return definitionId;}
    public UUID worldId(){return worldId;} public String worldName(){return worldName;} public BlockPos campAnchor(){return campAnchor;}
    public BlockPos siteAnchor(){return siteAnchor;} public long startedAt(){return startedAt;} public long expiresAt(){return expiresAt;}
    public Set<UUID> participants(){return participants;} public Set<BlockPos> objectives(){return objectives;}
    public Set<BlockPos> completed(){return completed;} public Map<BlockPos,SiteSnapshot> snapshots(){return snapshots;}
    public Set<UUID> spawnedMobs(){return spawnedMobs;} public int kills(){return kills;} public void kills(int v){kills=Math.max(0,v);}
    public ExpeditionStatus status(){return status;} public void status(ExpeditionStatus v){status=v;}
    public World world(){World w=Bukkit.getWorld(worldId); return w==null?Bukkit.getWorld(worldName):w;}
    public Location siteLocation(){World w=world(); return w==null?null:siteAnchor.location(w).add(.5,1,.5);}
    public Location campLocation(){World w=world(); return w==null?null:campAnchor.location(w).add(.5,1,.5);}
    public int objectiveProgress(){return completed.size();}
}
