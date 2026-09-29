package ru.neverland.townycrime.api;
import java.util.*;
import org.bukkit.event.*;
/** Notification after durable application, on the main thread. Receivers must deduplicate ID
 * if persisting their own effects; this notification is not a delivery queue. */
public final class CrimeIncidentEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();
    private final UUID id,town;private final String kind,resource;private final long amount;
    public CrimeIncidentEvent(UUID id,UUID town,String kind,String resource,long amount){this.id=id;this.town=town;this.kind=kind;this.resource=resource;this.amount=amount;}
    public UUID id(){ synchronized (ru.neverland.core.SuiteState.lock()) {return id;} }public UUID town(){ synchronized (ru.neverland.core.SuiteState.lock()) {return town;} }public String kind(){ synchronized (ru.neverland.core.SuiteState.lock()) {return kind;} }public String resource(){ synchronized (ru.neverland.core.SuiteState.lock()) {return resource;} }public long amount(){ synchronized (ru.neverland.core.SuiteState.lock()) {return amount;} }
    @Override public HandlerList getHandlers(){ synchronized (ru.neverland.core.SuiteState.lock()) {return HANDLERS;} }public static HandlerList getHandlerList(){ synchronized (ru.neverland.core.SuiteState.lock()) {return HANDLERS;} }
}
