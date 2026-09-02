package ru.neverland.townybuilds;

import org.bukkit.Axis;
import org.bukkit.block.BlockFace;
import ru.neverland.townybuilds.construction.ConstructionSite;

import java.util.UUID;

public final class BlockOrientationSmoke {
    public static void main(String[] args) {
        for (BlockFace facing : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            ConstructionSite site = new ConstructionSite("test", UUID.randomUUID(), 0, 64, 0,
                    facing, 0, 1, 1, true);
            if (site.worldFace(BlockFace.NORTH) != facing) throw new AssertionError("front mismatch: " + facing);
        }
        ConstructionSite east = new ConstructionSite("test", UUID.randomUUID(), 0, 64, 0,
                BlockFace.EAST, 0, 1, 1, true);
        if (east.worldFace(BlockFace.EAST) != BlockFace.SOUTH) throw new AssertionError("right rotation failed");
        if (east.worldAxis(Axis.X) != Axis.Z || east.worldAxis(Axis.Z) != Axis.X) {
            throw new AssertionError("axis rotation failed");
        }
        if (east.worldAxis(Axis.Y) != Axis.Y) throw new AssertionError("vertical axis rotated");
        if (east.architectureVersion() != ConstructionSite.CURRENT_ARCHITECTURE_VERSION) {
            throw new AssertionError("Новая площадка получила устаревшую версию архитектуры");
        }
        if (ConstructionSite.CURRENT_ARCHITECTURE_VERSION != 5) {
            throw new AssertionError("Не активирована архитектура второй очереди зданий");
        }
        ConstructionSite legacy = new ConstructionSite("test", UUID.randomUUID(), 0, 64, 0,
                BlockFace.NORTH, 1, 1, 2, false, 1);
        if (legacy.architectureVersion() != 1) throw new AssertionError("Не распознана старая площадка");
        legacy.setArchitectureVersion(ConstructionSite.CURRENT_ARCHITECTURE_VERSION);
        if (legacy.architectureVersion() != ConstructionSite.CURRENT_ARCHITECTURE_VERSION) {
            throw new AssertionError("Версия архитектуры не обновилась");
        }
    }
}
