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

    public UUID ownerId() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return ownerId; } }
    public String ownerName() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return ownerName; } }
    public void ownerName(String value) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { ownerName = value; } }
    public UUID worldId() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return worldId; } }
    public String worldName() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return worldName; } }
    public BlockPos anchor() { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { return anchor; } }
    public BlockFace facing() { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return facing; } }
    public int level() { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { return level; } }
    public void level(int value) { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) { level = Math.max(1, Math.min(3, value)); } }
    public String styleId() { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) { return styleId; } }
    public long createdAt() { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) { return createdAt; } }
    public long burnUntil() { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) { return burnUntil; } }
    public void burnUntil(long value) { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) { burnUntil = value; } }
    public boolean mobsDisabled() { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) { return mobsDisabled; } }
    public void mobsDisabled(boolean value) { try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) { mobsDisabled = value; } }
    public Map<UUID, String> trusted() { try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) { return trusted; } }
    public ItemStack[] stash() { try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) { return stash; } }
    public void stash(ItemStack[] value, int size) { try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {
        ItemStack[] resized = new ItemStack[Math.max(9, size)];
        if (value != null) System.arraycopy(value, 0, resized, 0, Math.min(value.length, resized.length));
        stash = resized;
    } }
    public Map<BlockPos, BlockSnapshot> snapshots() { try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) { return snapshots; } }
    public Set<BlockPos> placedBlocks() { try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) { return placedBlocks; } }
    public BlockPos stashBlock() { try (var suiteGuard21 = ru.neverland.core.SuiteState.guard()) { return stashBlock; } }
    public void stashBlock(BlockPos value) { try (var suiteGuard22 = ru.neverland.core.SuiteState.guard()) { stashBlock = value; } }
    public UUID hologramId() { try (var suiteGuard23 = ru.neverland.core.SuiteState.guard()) { return hologramId; } }
    public void hologramId(UUID value) { try (var suiteGuard24 = ru.neverland.core.SuiteState.guard()) { hologramId = value; } }

    public Optional<World> world() { try (var suiteGuard25 = ru.neverland.core.SuiteState.guard()) {
        World world = Bukkit.getWorld(worldId);
        if (world == null) world = Bukkit.getWorld(worldName);
        return Optional.ofNullable(world);
    } }

    public Location anchorLocation() { try (var suiteGuard26 = ru.neverland.core.SuiteState.guard()) {
        return world().map(anchor::location).orElse(null);
    } }

    public boolean canAccess(UUID playerId) { try (var suiteGuard27 = ru.neverland.core.SuiteState.guard()) {
        return ownerId.equals(playerId) || trusted.containsKey(playerId);
    } }

    public long remainingBurnMillis(long now) { try (var suiteGuard28 = ru.neverland.core.SuiteState.guard()) {
        return Math.max(0L, burnUntil - now);
    } }

    public long remainingLifeMillis(long now, long maximumLifeMillis) { try (var suiteGuard29 = ru.neverland.core.SuiteState.guard()) {
        return Math.max(0L, createdAt + maximumLifeMillis - now);
    } }

    public List<ItemStack> nonEmptyStash() { try (var suiteGuard30 = ru.neverland.core.SuiteState.guard()) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : stash) if (item != null && !item.getType().isAir()) result.add(item.clone());
        return result;
    } }
}
