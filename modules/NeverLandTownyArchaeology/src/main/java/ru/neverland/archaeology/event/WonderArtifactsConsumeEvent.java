package ru.neverland.archaeology.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

public final class WonderArtifactsConsumeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList(); private final UUID townId; private final String wonderId; private final UUID actorId; private final Map<String, Integer> requirements; private boolean cancelled;
    public WonderArtifactsConsumeEvent(UUID townId, String wonderId, UUID actorId, Map<String, Integer> requirements) { this.townId = townId; this.wonderId = wonderId; this.actorId = actorId; this.requirements = Map.copyOf(requirements); }
    public UUID townId() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return townId; } } public String wonderId() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return wonderId; } } public UUID actorId() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return actorId; } } public Map<String, Integer> requirements() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return requirements; } }
    @Override public boolean isCancelled() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return cancelled; } } @Override public void setCancelled(boolean value) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { cancelled = value; } }
    @Override public @NotNull HandlerList getHandlers() { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return HANDLERS; } } public static HandlerList getHandlerList() { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { return HANDLERS; } }
}
