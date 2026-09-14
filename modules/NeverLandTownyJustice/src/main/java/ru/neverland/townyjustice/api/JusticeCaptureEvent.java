package ru.neverland.townyjustice.api;
import java.util.UUID;
import org.bukkit.event.*;
/** Cancellable before a custody intent or Towny jailing; integrations can enforce their own protection rules. */
public final class JusticeCaptureEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS=new HandlerList();private boolean cancelled;private final UUID caseId,town,target,hunter;
    public JusticeCaptureEvent(UUID id,UUID town,UUID target,UUID hunter){caseId=id;this.town=town;this.target=target;this.hunter=hunter;}
    public UUID caseId(){return caseId;}public UUID town(){return town;}public UUID target(){return target;}public UUID hunter(){return hunter;}
    @Override public boolean isCancelled(){return cancelled;}@Override public void setCancelled(boolean c){cancelled=c;}@Override public HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
