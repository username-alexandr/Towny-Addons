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
    public UUID townId() { return townId; } public String wonderId() { return wonderId; } public UUID actorId() { return actorId; } public Map<String, Integer> requirements() { return requirements; }
    @Override public boolean isCancelled() { return cancelled; } @Override public void setCancelled(boolean value) { cancelled = value; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; } public static HandlerList getHandlerList() { return HANDLERS; }
}
