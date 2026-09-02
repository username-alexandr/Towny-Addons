package ru.neverland.mintcamps.util;

import org.bukkit.block.BlockFace;
import ru.neverland.mintcamps.model.BlockPos;

public final class DirectionUtil {
    private DirectionUtil() {
    }

    public static BlockFace cardinal(float yaw) {
        int index = Math.floorMod(Math.round(yaw / 90.0F), 4);
        return switch (index) {
            case 0 -> BlockFace.SOUTH;
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }

    public static BlockPos rotate(BlockPos anchor, BlockFace forward, BlockPos relative) {
        int fx = forward.getModX();
        int fz = forward.getModZ();
        int rx = -fz;
        int rz = fx;
        return new BlockPos(
                anchor.x() + relative.x() * rx + relative.z() * fx,
                anchor.y() + relative.y(),
                anchor.z() + relative.x() * rz + relative.z() * fz
        );
    }

    public static BlockFace relative(BlockFace forward, int quarterTurnsRight) {
        BlockFace value = forward;
        for (int index = 0; index < Math.floorMod(quarterTurnsRight, 4); index++) {
            value = switch (value) {
                case NORTH -> BlockFace.EAST;
                case EAST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.WEST;
                default -> BlockFace.NORTH;
            };
        }
        return value;
    }
}
