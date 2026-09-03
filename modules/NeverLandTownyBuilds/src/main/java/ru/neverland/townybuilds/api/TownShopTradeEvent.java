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

    public UUID buyerId() { return buyerId; }
    public UUID sellerTownId() { return sellerTownId; }
    public ItemStack item() { return item.clone(); }
    public double total() { return total; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean value) { cancelled = value; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
