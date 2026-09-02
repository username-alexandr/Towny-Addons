package ru.neverland.archaeology.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class DigSite {
    private final UUID id; private final String type; private final UUID worldId; private final String worldName; private final int x; private final int y; private final int z; private final long createdAt;
    private final Set<String> blocks = new LinkedHashSet<>(); private boolean completed;
    public DigSite(UUID id, String type, UUID worldId, String worldName, int x, int y, int z, long createdAt) { this.id = id; this.type = type; this.worldId = worldId; this.worldName = worldName; this.x = x; this.y = y; this.z = z; this.createdAt = createdAt; }
    public UUID id() { return id; } public String type() { return type; } public UUID worldId() { return worldId; } public String worldName() { return worldName; }
    public int x() { return x; } public int y() { return y; } public int z() { return z; } public long createdAt() { return createdAt; }
    public Set<String> blocks() { return blocks; } public boolean completed() { return completed; } public void completed(boolean value) { completed = value; }
    public static String blockKey(int x, int y, int z) { return x + "," + y + "," + z; }
}
