package ru.neverland.mintcontracts.orders;

import java.util.*;

/** Influences only the next draw. Reward, rarity, quota and accepted snapshots are unchanged. */
public final class DemandPriority {
    private volatile Map<String,Long> missing=Map.of();
    public void update(Map<String,Long> amounts){missing=Map.copyOf(amounts);}
    public int factor(PublicOrders.Template template){
        if(template.type()!=PublicOrders.Type.DELIVERY)return 1;
        long needed=missing.getOrDefault(template.target(),0L);if(needed<=0)return 1;
        return 1+(int)Math.min(4,1+(needed-1)/template.amount());
    }
}
