package ru.neverland.townybuilds.api;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Синхронное событие покупки в городской лавке для налогов, репутации и летописи. */
public final class TownShopTradeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID buyerId;
    private final UUID sellerTownId;
    private final ItemStack item;
    private final double total;
    private boolean cancelled;

    public TownShopTradeEvent(UUID buyerId, UUID sellerTownId, ItemStack item, double total) {
        this.buyerId = buyerId;
        this.sellerTownId = sellerTownId;
        this.item = item.clone();
        this.total = total;
    }

    public UUID buyerId() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return buyerId; } }
    public UUID sellerTownId() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return sellerTownId; } }
    public ItemStack item() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return item.clone(); } }
    public double total() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return total; } }
    @Override public boolean isCancelled() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return cancelled; } }
    @Override public void setCancelled(boolean value) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { cancelled = value; } }
    @Override public @NotNull HandlerList getHandlers() { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return HANDLERS; } }
    public static @NotNull HandlerList getHandlerList() { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { return HANDLERS; } }
}
