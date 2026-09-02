package ru.neverland.mintcamps.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class Camp {
    private final UUID ownerId;
    private String ownerName;
    private final UUID worldId;
    private final String worldName;
    private final BlockPos anchor;
    private final BlockFace facing;
    private int level;
    private final String styleId;
    private final long createdAt;
    private long burnUntil;
    private boolean mobsDisabled;
    private final Map<UUID, String> trusted = new LinkedHashMap<>();
    private ItemStack[] stash;
    private final Map<BlockPos, BlockSnapshot> snapshots = new LinkedHashMap<>();
    private final Set<BlockPos> placedBlocks = new LinkedHashSet<>();
    private BlockPos stashBlock;
    private UUID hologramId;

    public Camp(UUID ownerId, String ownerName, UUID worldId, String worldName, BlockPos anchor,
                BlockFace facing, int level, String styleId, long createdAt, long burnUntil,
                boolean mobsDisabled, int stashSize) {
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.worldId = worldId;
        this.worldName = worldName;
        this.anchor = anchor;
        this.facing = facing;
        this.level = level;
        this.styleId = styleId;
        this.createdAt = createdAt;
        this.burnUntil = burnUntil;
        this.mobsDisabled = mobsDisabled;
        this.stash = new ItemStack[Math.max(9, stashSize)];
    }

    public UUID ownerId() { return ownerId; }
    public String ownerName() { return ownerName; }
    public void ownerName(String value) { ownerName = value; }
    public UUID worldId() { return worldId; }
    public String worldName() { return worldName; }
    public BlockPos anchor() { return anchor; }
    public BlockFace facing() { return facing; }
    public int level() { return level; }
    public void level(int value) { level = Math.max(1, Math.min(3, value)); }
    public String styleId() { return styleId; }
    public long createdAt() { return createdAt; }
    public long burnUntil() { return burnUntil; }
    public void burnUntil(long value) { burnUntil = value; }
    public boolean mobsDisabled() { return mobsDisabled; }
    public void mobsDisabled(boolean value) { mobsDisabled = value; }
    public Map<UUID, String> trusted() { return trusted; }
    public ItemStack[] stash() { return stash; }
    public void stash(ItemStack[] value, int size) {
        ItemStack[] resized = new ItemStack[Math.max(9, size)];
        if (value != null) System.arraycopy(value, 0, resized, 0, Math.min(value.length, resized.length));
        stash = resized;
    }
    public Map<BlockPos, BlockSnapshot> snapshots() { return snapshots; }
    public Set<BlockPos> placedBlocks() { return placedBlocks; }
    public BlockPos stashBlock() { return stashBlock; }
    public void stashBlock(BlockPos value) { stashBlock = value; }
    public UUID hologramId() { return hologramId; }
    public void hologramId(UUID value) { hologramId = value; }

    public Optional<World> world() {
        World world = Bukkit.getWorld(worldId);
        if (world == null) world = Bukkit.getWorld(worldName);
        return Optional.ofNullable(world);
    }

    public Location anchorLocation() {
        return world().map(anchor::location).orElse(null);
    }

    public boolean canAccess(UUID playerId) {
        return ownerId.equals(playerId) || trusted.containsKey(playerId);
    }

    public long remainingBurnMillis(long now) {
        return Math.max(0L, burnUntil - now);
    }

    public long remainingLifeMillis(long now, long maximumLifeMillis) {
        return Math.max(0L, createdAt + maximumLifeMillis - now);
    }

    public List<ItemStack> nonEmptyStash() {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : stash) if (item != null && !item.getType().isAir()) result.add(item.clone());
        return result;
    }
}
