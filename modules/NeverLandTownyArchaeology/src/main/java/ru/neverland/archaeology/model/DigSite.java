package ru.neverland.archaeology.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class DigSite {
    private final UUID id; private final String type; private final UUID worldId; private final String worldName; private final int x; private final int y; private final int z; private final long createdAt;
    private final Set<String> blocks = new LinkedHashSet<>(); private boolean completed;
    public DigSite(UUID id, String type, UUID worldId, String worldName, int x, int y, int z, long createdAt) { this.id = id; this.type = type; this.worldId = worldId; this.worldName = worldName; this.x = x; this.y = y; this.z = z; this.createdAt = createdAt; }
    public UUID id() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return id; } } public String type() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return type; } } public UUID worldId() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return worldId; } } public String worldName() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return worldName; } }
    public int x() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return x; } } public int y() { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { return y; } } public int z() { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return z; } } public long createdAt() { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { return createdAt; } }
    public Set<String> blocks() { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) { return blocks; } } public boolean completed() { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) { return completed; } } public void completed(boolean value) { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) { completed = value; } }
    public static String blockKey(int x, int y, int z) { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) { return x + "," + y + "," + z; } }
}
