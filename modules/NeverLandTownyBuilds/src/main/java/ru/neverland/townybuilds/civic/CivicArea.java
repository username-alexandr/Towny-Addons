package ru.neverland.townybuilds.civic;

import java.util.UUID;

/** Прямоугольная рабочая территория городской службы. */
public record CivicArea(UUID worldId, int minX, int maxX, int minZ, int maxZ) {
    public CivicArea {
        if (worldId == null) throw new IllegalArgumentException("Не указан мир территории");
        if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("Перепутаны границы территории");
    }

    public int chunkCount() {
        return (Math.floorDiv(maxX, 16) - Math.floorDiv(minX, 16) + 1)
                * (Math.floorDiv(maxZ, 16) - Math.floorDiv(minZ, 16) + 1);
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}
