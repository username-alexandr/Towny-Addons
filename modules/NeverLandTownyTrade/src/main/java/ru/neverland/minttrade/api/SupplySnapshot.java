package ru.neverland.minttrade.api;
import java.util.*;
public record SupplySnapshot(UUID id,UUID seller,UUID buyer,String itemName,int amount,long priceCents,int days,
                             String status,Set<UUID> pausedBy,long nextDue,long delivered,String paymentState,String note) {
    public SupplySnapshot {pausedBy=Set.copyOf(pausedBy);}
}
