package ru.neverland.morstownstick.model;

public record BorderEdge(String world, double x1, double z1, double x2, double z2) {
    public double distanceSquared(double x, double z) {
        double closestX = Math.max(Math.min(x, Math.max(x1, x2)), Math.min(x1, x2));
        double closestZ = Math.max(Math.min(z, Math.max(z1, z2)), Math.min(z1, z2));
        double dx = x - closestX;
        double dz = z - closestZ;
        return dx * dx + dz * dz;
    }
}
