package ru.neverland.townychronicles.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import ru.neverland.townychronicles.model.ChronicleEntry;

public final class ChronicleRecordEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();private final ChronicleEntry entry;public ChronicleRecordEvent(ChronicleEntry entry){this.entry=entry;}public ChronicleEntry entry(){return entry;}@Override public @NotNull HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
