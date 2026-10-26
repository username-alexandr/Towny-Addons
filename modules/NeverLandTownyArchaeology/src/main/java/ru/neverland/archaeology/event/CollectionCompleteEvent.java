package ru.neverland.archaeology.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class CollectionCompleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList(); private final UUID townId; private final String collectionId;
    public CollectionCompleteEvent(UUID townId, String collectionId) { this.townId = townId; this.collectionId = collectionId; }
    public UUID townId() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return townId; } } public String collectionId() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return collectionId; } }
    @Override public @NotNull HandlerList getHandlers() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return HANDLERS; } } public static HandlerList getHandlerList() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return HANDLERS; } }
}
