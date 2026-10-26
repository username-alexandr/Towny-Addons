package ru.neverland.archaeology.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class ArtifactDiscoverEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList(); private final Player player; private final String artifactId; private final boolean firstDiscovery;
    public ArtifactDiscoverEvent(Player player, String artifactId, boolean firstDiscovery) { this.player = player; this.artifactId = artifactId; this.firstDiscovery = firstDiscovery; }
    public Player player() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return player; } } public String artifactId() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return artifactId; } } public boolean firstDiscovery() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return firstDiscovery; } }
    @Override public @NotNull HandlerList getHandlers() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return HANDLERS; } } public static HandlerList getHandlerList() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return HANDLERS; } }
}
