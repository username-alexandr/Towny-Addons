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
    public ReputationScope scope() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return scope; } }
    public UUID first() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return first; } }
    public UUID second() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return second; } }
    public int oldScore() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return oldScore; } }
    public int requestedScore() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return requestedScore; } }
    public int requestedDelta() { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { return requestedScore - oldScore; } }
    public String source() { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return source; } }
    public String reason() { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { return reason; } }
    @Override public boolean isCancelled() { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) { return cancelled; } }
    @Override public void setCancelled(boolean cancelled) { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) { this.cancelled = cancelled; } }
    @Override public @NotNull HandlerList getHandlers() { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) { return HANDLERS; } }
    public static HandlerList getHandlerList() { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) { return HANDLERS; } }
}
