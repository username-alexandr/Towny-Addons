package ru.neverland.townybuilds.civic;

import java.util.UUID;

/** Два конца линейного городского объекта: стены, рва или дамбы. */
public record CivicLine(UUID worldId, int x1, int y1, int z1, int x2, int y2, int z2) {
    public CivicLine {
        if (worldId == null) throw new IllegalArgumentException("Не указан мир линии");
    }

    public int length() {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int dz = z2 - z1;
        return (int) Math.ceil(Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz)) + 1;
    }
}
