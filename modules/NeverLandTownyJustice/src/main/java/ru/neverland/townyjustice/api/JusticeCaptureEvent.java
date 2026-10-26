package ru.neverland.townyjustice.api;
import java.util.UUID;
import org.bukkit.event.*;
/** Cancellable before a custody intent or Towny jailing; integrations can enforce their own protection rules. */
public final class JusticeCaptureEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS=new HandlerList();private boolean cancelled;private final UUID caseId,town,target,hunter;
    public JusticeCaptureEvent(UUID id,UUID town,UUID target,UUID hunter){caseId=id;this.town=town;this.target=target;this.hunter=hunter;}
    public UUID caseId(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return caseId;} }public UUID town(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return town;} }public UUID target(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return target;} }public UUID hunter(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return hunter;} }
    @Override public boolean isCancelled(){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {return cancelled;} }@Override public void setCancelled(boolean c){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {cancelled=c;} }@Override public HandlerList getHandlers(){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {return HANDLERS;} }public static HandlerList getHandlerList(){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {return HANDLERS;} }
}
