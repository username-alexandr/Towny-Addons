package ru.neverland.archaeology.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class ArtifactDonateEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList(); private final Player player; private final UUID townId; private final String artifactId; private final int amount; private boolean cancelled;
    public ArtifactDonateEvent(Player player, UUID townId, String artifactId) { this(player, townId, artifactId, 1); }
    public ArtifactDonateEvent(Player player, UUID townId, String artifactId, int amount) { this.player = player; this.townId = townId; this.artifactId = artifactId; this.amount = Math.max(1, amount); }
    public Player player() { synchronized (ru.neverland.core.SuiteState.lock()) { return player; } } public UUID townId() { synchronized (ru.neverland.core.SuiteState.lock()) { return townId; } } public String artifactId() { synchronized (ru.neverland.core.SuiteState.lock()) { return artifactId; } } public int amount() { synchronized (ru.neverland.core.SuiteState.lock()) { return amount; } }
    @Override public boolean isCancelled() { synchronized (ru.neverland.core.SuiteState.lock()) { return cancelled; } } @Override public void setCancelled(boolean value) { synchronized (ru.neverland.core.SuiteState.lock()) { cancelled = value; } }
    @Override public @NotNull HandlerList getHandlers() { synchronized (ru.neverland.core.SuiteState.lock()) { return HANDLERS; } } public static HandlerList getHandlerList() { synchronized (ru.neverland.core.SuiteState.lock()) { return HANDLERS; } }
}
