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
    public ReputationScope scope() { synchronized (ru.neverland.core.SuiteState.lock()) { return scope; } }
    public UUID first() { synchronized (ru.neverland.core.SuiteState.lock()) { return first; } }
    public UUID second() { synchronized (ru.neverland.core.SuiteState.lock()) { return second; } }
    public int oldScore() { synchronized (ru.neverland.core.SuiteState.lock()) { return oldScore; } }
    public int requestedScore() { synchronized (ru.neverland.core.SuiteState.lock()) { return requestedScore; } }
    public int requestedDelta() { synchronized (ru.neverland.core.SuiteState.lock()) { return requestedScore - oldScore; } }
    public String source() { synchronized (ru.neverland.core.SuiteState.lock()) { return source; } }
    public String reason() { synchronized (ru.neverland.core.SuiteState.lock()) { return reason; } }
    @Override public boolean isCancelled() { synchronized (ru.neverland.core.SuiteState.lock()) { return cancelled; } }
    @Override public void setCancelled(boolean cancelled) { synchronized (ru.neverland.core.SuiteState.lock()) { this.cancelled = cancelled; } }
    @Override public @NotNull HandlerList getHandlers() { synchronized (ru.neverland.core.SuiteState.lock()) { return HANDLERS; } }
    public static HandlerList getHandlerList() { synchronized (ru.neverland.core.SuiteState.lock()) { return HANDLERS; } }
}
