package ru.neverland.archaeology.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class ArtifactDiscoverEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList(); private final Player player; private final String artifactId; private final boolean firstDiscovery;
    public ArtifactDiscoverEvent(Player player, String artifactId, boolean firstDiscovery) { this.player = player; this.artifactId = artifactId; this.firstDiscovery = firstDiscovery; }
    public Player player() { synchronized (ru.neverland.core.SuiteState.lock()) { return player; } } public String artifactId() { synchronized (ru.neverland.core.SuiteState.lock()) { return artifactId; } } public boolean firstDiscovery() { synchronized (ru.neverland.core.SuiteState.lock()) { return firstDiscovery; } }
    @Override public @NotNull HandlerList getHandlers() { synchronized (ru.neverland.core.SuiteState.lock()) { return HANDLERS; } } public static HandlerList getHandlerList() { synchronized (ru.neverland.core.SuiteState.lock()) { return HANDLERS; } }
}
