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
    public UUID townId() { synchronized (ru.neverland.core.SuiteState.lock()) { return townId; } } public String wonderId() { synchronized (ru.neverland.core.SuiteState.lock()) { return wonderId; } } public UUID actorId() { synchronized (ru.neverland.core.SuiteState.lock()) { return actorId; } } public Map<String, Integer> requirements() { synchronized (ru.neverland.core.SuiteState.lock()) { return requirements; } }
    @Override public boolean isCancelled() { synchronized (ru.neverland.core.SuiteState.lock()) { return cancelled; } } @Override public void setCancelled(boolean value) { synchronized (ru.neverland.core.SuiteState.lock()) { cancelled = value; } }
    @Override public @NotNull HandlerList getHandlers() { synchronized (ru.neverland.core.SuiteState.lock()) { return HANDLERS; } } public static HandlerList getHandlerList() { synchronized (ru.neverland.core.SuiteState.lock()) { return HANDLERS; } }
}
