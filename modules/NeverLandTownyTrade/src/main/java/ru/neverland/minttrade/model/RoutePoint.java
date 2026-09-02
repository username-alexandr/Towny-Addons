package ru.neverland.minttrade.model;

import java.util.UUID;

public record RoutePoint(UUID worldId, String worldName, double x, double y, double z,
                         Kind kind, String label, int level, UUID ownerId) {
    public enum Kind { TOWN, CAMP }
    public double distance(RoutePoint other) {
        double dx = x - other.x, dy = y - other.y, dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
