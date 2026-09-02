package ru.neverland.reputation.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import ru.neverland.reputation.model.ReputationScope;

import java.util.UUID;

public final class ReputationChangeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ReputationScope scope; private final UUID first; private final UUID second;
    private final int oldScore; private final int requestedScore; private final String source; private final String reason;
    private boolean cancelled;
    public ReputationChangeEvent(ReputationScope scope, UUID first, UUID second, int oldScore, int requestedScore, String source, String reason) {
        this.scope = scope; this.first = first; this.second = second; this.oldScore = oldScore; this.requestedScore = requestedScore; this.source = source; this.reason = reason;
    }
    public ReputationScope scope() { return scope; }
    public UUID first() { return first; }
    public UUID second() { return second; }
    public int oldScore() { return oldScore; }
    public int requestedScore() { return requestedScore; }
    public int requestedDelta() { return requestedScore - oldScore; }
    public String source() { return source; }
    public String reason() { return reason; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
