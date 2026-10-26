package ru.neverland.townycrime.api;
import java.util.*;
import org.bukkit.event.*;
/** Notification after durable application, on the main thread. Receivers must deduplicate ID
 * if persisting their own effects; this notification is not a delivery queue. */
public final class CrimeIncidentEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();
    private final UUID id,town;private final String kind,resource;private final long amount;
    public CrimeIncidentEvent(UUID id,UUID town,String kind,String resource,long amount){this.id=id;this.town=town;this.kind=kind;this.resource=resource;this.amount=amount;}
    public UUID id(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return id;} }public UUID town(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return town;} }public String kind(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return kind;} }public String resource(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return resource;} }public long amount(){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {return amount;} }
    @Override public HandlerList getHandlers(){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {return HANDLERS;} }public static HandlerList getHandlerList(){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {return HANDLERS;} }
}
