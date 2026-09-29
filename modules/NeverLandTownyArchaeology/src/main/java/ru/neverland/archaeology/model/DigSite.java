package ru.neverland.archaeology.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class DigSite {
    private final UUID id; private final String type; private final UUID worldId; private final String worldName; private final int x; private final int y; private final int z; private final long createdAt;
    private final Set<String> blocks = new LinkedHashSet<>(); private boolean completed;
    public DigSite(UUID id, String type, UUID worldId, String worldName, int x, int y, int z, long createdAt) { this.id = id; this.type = type; this.worldId = worldId; this.worldName = worldName; this.x = x; this.y = y; this.z = z; this.createdAt = createdAt; }
    public UUID id() { synchronized (ru.neverland.core.SuiteState.lock()) { return id; } } public String type() { synchronized (ru.neverland.core.SuiteState.lock()) { return type; } } public UUID worldId() { synchronized (ru.neverland.core.SuiteState.lock()) { return worldId; } } public String worldName() { synchronized (ru.neverland.core.SuiteState.lock()) { return worldName; } }
    public int x() { synchronized (ru.neverland.core.SuiteState.lock()) { return x; } } public int y() { synchronized (ru.neverland.core.SuiteState.lock()) { return y; } } public int z() { synchronized (ru.neverland.core.SuiteState.lock()) { return z; } } public long createdAt() { synchronized (ru.neverland.core.SuiteState.lock()) { return createdAt; } }
    public Set<String> blocks() { synchronized (ru.neverland.core.SuiteState.lock()) { return blocks; } } public boolean completed() { synchronized (ru.neverland.core.SuiteState.lock()) { return completed; } } public void completed(boolean value) { synchronized (ru.neverland.core.SuiteState.lock()) { completed = value; } }
    public static String blockKey(int x, int y, int z) { synchronized (ru.neverland.core.SuiteState.lock()) { return x + "," + y + "," + z; } }
}
