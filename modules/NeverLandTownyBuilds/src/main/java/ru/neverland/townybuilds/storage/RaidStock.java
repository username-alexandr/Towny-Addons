package ru.neverland.townybuilds.storage;

import java.util.*;
import org.bukkit.inventory.ItemStack;

/** Kept outside TownData so deletion of a town cannot erase reserved cargo. */
public record RaidStock(UUID id,UUID defender,UUID attacker,UUID actor,int basisPoints,ItemStack[] cargo,String state) {
    public RaidStock {
        Objects.requireNonNull(id);Objects.requireNonNull(defender);Objects.requireNonNull(attacker);Objects.requireNonNull(actor);
        if(defender.equals(attacker)||basisPoints<0||basisPoints>10000||cargo==null||cargo.length>1000||!Set.of("RESERVED","DELIVERED","RETURNED").contains(state))throw new IllegalArgumentException("Некорректная добыча рейда");
        cargo=StockMath.copy(cargo);
        for(var item:cargo)if(!StockMath.present(item)||item.getAmount()>item.getMaxStackSize())throw new IllegalArgumentException("Некорректный предмет добычи");
    }
    @Override public ItemStack[] cargo(){return StockMath.copy(cargo);}
    public RaidStock finish(boolean deliver){return new RaidStock(id,defender,attacker,actor,basisPoints,cargo,deliver?"DELIVERED":"RETURNED");}
    public Map<String,Object> snapshot(){return Map.of("state",state,"amount",StockMath.total(cargo),"stacks",cargo.length,"attacker",attacker,"defender",defender);}
}
