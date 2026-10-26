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
    public Player player() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return player; } } public UUID townId() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return townId; } } public String artifactId() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return artifactId; } } public int amount() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return amount; } }
    @Override public boolean isCancelled() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return cancelled; } } @Override public void setCancelled(boolean value) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { cancelled = value; } }
    @Override public @NotNull HandlerList getHandlers() { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return HANDLERS; } } public static HandlerList getHandlerList() { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { return HANDLERS; } }
}
