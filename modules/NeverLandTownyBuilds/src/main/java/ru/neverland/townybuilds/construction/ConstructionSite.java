package ru.neverland.townybuilds.construction;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.UUID;

public final class ConstructionSite {
    public static final int CURRENT_ARCHITECTURE_VERSION = 6;
    private final String projectId;
    private final UUID worldId;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final BlockFace facing;
    private int completedStage;
    private int targetStage;
    private int buildFromStage;
    private boolean active;
    private int architectureVersion;

    public ConstructionSite(String projectId, UUID worldId, int originX, int originY, int originZ,
                            BlockFace facing, int completedStage, int targetStage,
                            int buildFromStage, boolean active) {
        this(projectId, worldId, originX, originY, originZ, facing, completedStage, targetStage,
                buildFromStage, active, CURRENT_ARCHITECTURE_VERSION);
    }

    public ConstructionSite(String projectId, UUID worldId, int originX, int originY, int originZ,
                            BlockFace facing, int completedStage, int targetStage,
                            int buildFromStage, boolean active, int architectureVersion) {
        this.projectId = projectId;
        this.worldId = worldId;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.facing = cardinal(facing);
        this.completedStage = Math.max(0, completedStage);
        this.targetStage = Math.max(this.completedStage, targetStage);
        this.buildFromStage = Math.max(1, buildFromStage);
        this.active = active;
        this.architectureVersion = Math.max(1, architectureVersion);
    }

    public String projectId() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return projectId; } }
    public UUID worldId() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return worldId; } }
    public int originX() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return originX; } }
    public int originY() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return originY; } }
    public int originZ() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return originZ; } }
    public BlockFace facing() { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { return facing; } }
    public int completedStage() { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return completedStage; } }
    public int targetStage() { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { return targetStage; } }
    public int buildFromStage() { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) { return buildFromStage; } }
    public boolean active() { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) { return active; } }
    public int architectureVersion() { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) { return architectureVersion; } }

    public void setArchitectureVersion(int version) { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {
        architectureVersion = Math.max(1, version);
    } }

    public void activate(int targetStage, int buildFromStage) { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {
        this.targetStage = targetStage;
        this.buildFromStage = buildFromStage;
        this.active = true;
    } }

    public void complete() { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {
        completedStage = targetStage;
        buildFromStage = targetStage + 1;
        active = false;
    } }

    public Location location(World world, BlockOffset offset) { try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {
        int[] rotated = rotate(offset.x(), offset.z());
        return new Location(world, originX + rotated[0], originY + offset.y(), originZ + rotated[1]);
    } }

    public BlockOffset offset(Location location) { try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {
        int dx = location.getBlockX() - originX;
        int dz = location.getBlockZ() - originZ;
        int[] local = inverse(dx, dz);
        return new BlockOffset(local[0], location.getBlockY() - originY, local[1]);
    } }

    public BlockFace worldFace(BlockFace localFace) { try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {
        if (localFace == null || localFace == BlockFace.UP || localFace == BlockFace.DOWN) return localFace;
        return switch (facing) {
            case EAST -> switch (localFace) {
                case NORTH -> BlockFace.EAST;
                case EAST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.WEST;
                case WEST -> BlockFace.NORTH;
                default -> localFace;
            };
            case SOUTH -> switch (localFace) {
                case NORTH -> BlockFace.SOUTH;
                case EAST -> BlockFace.WEST;
                case SOUTH -> BlockFace.NORTH;
                case WEST -> BlockFace.EAST;
                default -> localFace;
            };
            case WEST -> switch (localFace) {
                case NORTH -> BlockFace.WEST;
                case EAST -> BlockFace.NORTH;
                case SOUTH -> BlockFace.EAST;
                case WEST -> BlockFace.SOUTH;
                default -> localFace;
            };
            default -> localFace;
        };
    } }

    public Axis worldAxis(Axis localAxis) { try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {
        if (localAxis == null || localAxis == Axis.Y) return localAxis;
        if (facing == BlockFace.EAST || facing == BlockFace.WEST) {
            return localAxis == Axis.X ? Axis.Z : Axis.X;
        }
        return localAxis;
    } }

    private int[] rotate(int x, int z) {
        return switch (facing) {
            case EAST -> new int[]{-z, x};
            case SOUTH -> new int[]{-x, -z};
            case WEST -> new int[]{z, -x};
            default -> new int[]{x, z};
        };
    }

    private int[] inverse(int x, int z) {
        return switch (facing) {
            case EAST -> new int[]{z, -x};
            case SOUTH -> new int[]{-x, -z};
            case WEST -> new int[]{-z, x};
            default -> new int[]{x, z};
        };
    }

    public static BlockFace cardinal(BlockFace face) { try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) {
        if (face == BlockFace.EAST || face == BlockFace.SOUTH || face == BlockFace.WEST) return face;
        return BlockFace.NORTH;
    } }
    private boolean adminPaused;
    public boolean adminPaused(){ try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) {return adminPaused;} }
    public void adminPaused(boolean value){ try (var suiteGuard21 = ru.neverland.core.SuiteState.guard()) {adminPaused=value;} }
}
