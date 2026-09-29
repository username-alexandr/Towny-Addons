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

    public UUID ownerId() { synchronized (ru.neverland.core.SuiteState.lock()) { return ownerId; } }
    public String ownerName() { synchronized (ru.neverland.core.SuiteState.lock()) { return ownerName; } }
    public void ownerName(String value) { synchronized (ru.neverland.core.SuiteState.lock()) { ownerName = value; } }
    public UUID worldId() { synchronized (ru.neverland.core.SuiteState.lock()) { return worldId; } }
    public String worldName() { synchronized (ru.neverland.core.SuiteState.lock()) { return worldName; } }
    public BlockPos anchor() { synchronized (ru.neverland.core.SuiteState.lock()) { return anchor; } }
    public BlockFace facing() { synchronized (ru.neverland.core.SuiteState.lock()) { return facing; } }
    public int level() { synchronized (ru.neverland.core.SuiteState.lock()) { return level; } }
    public void level(int value) { synchronized (ru.neverland.core.SuiteState.lock()) { level = Math.max(1, Math.min(3, value)); } }
    public String styleId() { synchronized (ru.neverland.core.SuiteState.lock()) { return styleId; } }
    public long createdAt() { synchronized (ru.neverland.core.SuiteState.lock()) { return createdAt; } }
    public long burnUntil() { synchronized (ru.neverland.core.SuiteState.lock()) { return burnUntil; } }
    public void burnUntil(long value) { synchronized (ru.neverland.core.SuiteState.lock()) { burnUntil = value; } }
    public boolean mobsDisabled() { synchronized (ru.neverland.core.SuiteState.lock()) { return mobsDisabled; } }
    public void mobsDisabled(boolean value) { synchronized (ru.neverland.core.SuiteState.lock()) { mobsDisabled = value; } }
    public Map<UUID, String> trusted() { synchronized (ru.neverland.core.SuiteState.lock()) { return trusted; } }
    public ItemStack[] stash() { synchronized (ru.neverland.core.SuiteState.lock()) { return stash; } }
    public void stash(ItemStack[] value, int size) { synchronized (ru.neverland.core.SuiteState.lock()) {
        ItemStack[] resized = new ItemStack[Math.max(9, size)];
        if (value != null) System.arraycopy(value, 0, resized, 0, Math.min(value.length, resized.length));
        stash = resized;
    } }
    public Map<BlockPos, BlockSnapshot> snapshots() { synchronized (ru.neverland.core.SuiteState.lock()) { return snapshots; } }
    public Set<BlockPos> placedBlocks() { synchronized (ru.neverland.core.SuiteState.lock()) { return placedBlocks; } }
    public BlockPos stashBlock() { synchronized (ru.neverland.core.SuiteState.lock()) { return stashBlock; } }
    public void stashBlock(BlockPos value) { synchronized (ru.neverland.core.SuiteState.lock()) { stashBlock = value; } }
    public UUID hologramId() { synchronized (ru.neverland.core.SuiteState.lock()) { return hologramId; } }
    public void hologramId(UUID value) { synchronized (ru.neverland.core.SuiteState.lock()) { hologramId = value; } }

    public Optional<World> world() { synchronized (ru.neverland.core.SuiteState.lock()) {
        World world = Bukkit.getWorld(worldId);
        if (world == null) world = Bukkit.getWorld(worldName);
        return Optional.ofNullable(world);
    } }

    public Location anchorLocation() { synchronized (ru.neverland.core.SuiteState.lock()) {
        return world().map(anchor::location).orElse(null);
    } }

    public boolean canAccess(UUID playerId) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return ownerId.equals(playerId) || trusted.containsKey(playerId);
    } }

    public long remainingBurnMillis(long now) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return Math.max(0L, burnUntil - now);
    } }

    public long remainingLifeMillis(long now, long maximumLifeMillis) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return Math.max(0L, createdAt + maximumLifeMillis - now);
    } }

    public List<ItemStack> nonEmptyStash() { synchronized (ru.neverland.core.SuiteState.lock()) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : stash) if (item != null && !item.getType().isAir()) result.add(item.clone());
        return result;
    } }
}
