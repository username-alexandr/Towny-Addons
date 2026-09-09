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

    public String projectId() { return projectId; }
    public UUID worldId() { return worldId; }
    public int originX() { return originX; }
    public int originY() { return originY; }
    public int originZ() { return originZ; }
    public BlockFace facing() { return facing; }
    public int completedStage() { return completedStage; }
    public int targetStage() { return targetStage; }
    public int buildFromStage() { return buildFromStage; }
    public boolean active() { return active; }
    public int architectureVersion() { return architectureVersion; }

    public void setArchitectureVersion(int version) {
        architectureVersion = Math.max(1, version);
    }

    public void activate(int targetStage, int buildFromStage) {
        this.targetStage = targetStage;
        this.buildFromStage = buildFromStage;
        this.active = true;
    }

    public void complete() {
        completedStage = targetStage;
        buildFromStage = targetStage + 1;
        active = false;
    }

    public Location location(World world, BlockOffset offset) {
        int[] rotated = rotate(offset.x(), offset.z());
        return new Location(world, originX + rotated[0], originY + offset.y(), originZ + rotated[1]);
    }

    public BlockOffset offset(Location location) {
        int dx = location.getBlockX() - originX;
        int dz = location.getBlockZ() - originZ;
        int[] local = inverse(dx, dz);
        return new BlockOffset(local[0], location.getBlockY() - originY, local[1]);
    }

    public BlockFace worldFace(BlockFace localFace) {
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
    }

    public Axis worldAxis(Axis localAxis) {
        if (localAxis == null || localAxis == Axis.Y) return localAxis;
        if (facing == BlockFace.EAST || facing == BlockFace.WEST) {
            return localAxis == Axis.X ? Axis.Z : Axis.X;
        }
        return localAxis;
    }

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

    public static BlockFace cardinal(BlockFace face) {
        if (face == BlockFace.EAST || face == BlockFace.SOUTH || face == BlockFace.WEST) return face;
        return BlockFace.NORTH;
    }
}
