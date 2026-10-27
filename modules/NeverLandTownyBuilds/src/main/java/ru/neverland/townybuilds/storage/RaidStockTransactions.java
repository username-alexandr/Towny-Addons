package ru.neverland.townybuilds.storage;

import java.io.IOException;
import java.util.*;
import org.bukkit.inventory.ItemStack;

/** Cargo reservation/removal and receipt share one atomic Builds snapshot. */
public final class RaidStockTransactions {
    public interface Access {
        ItemStack[] read(UUID town);void write(UUID town,ItemStack[] items);
        RaidStock get(UUID operation);void put(UUID operation,RaidStock stock);void commit()throws IOException;
    }
    private RaidStockTransactions(){}
    public static RaidStock reserve(Access a,UUID id,UUID defender,UUID attacker,int bps)throws IOException {
        return reserve(a,id,defender,attacker,new UUID(0,0),bps);
    }
    public static RaidStock reserve(Access a,UUID id,UUID defender,UUID attacker,UUID actor,int bps)throws IOException {
        if(defender.equals(attacker)||bps<0||bps>10000)throw new IllegalArgumentException("Неверная доля склада");
        var old=a.get(id);if(old!=null){if(!old.defender().equals(defender)||!old.attacker().equals(attacker)||!old.actor().equals(actor)||old.basisPoints()!=bps)throw new IllegalArgumentException("ID добычи занят");return old;}
        ItemStack[] before=a.read(defender),work=StockMath.copy(before);List<ItemStack> samples=new ArrayList<>(),cargo=new ArrayList<>();
        for(var item:before)if(StockMath.present(item)&&samples.stream().noneMatch(s->s.isSimilar(item))){var sample=item.clone();sample.setAmount(1);samples.add(sample);}
        for(var sample:samples){int count=(int)((long)StockMath.count(work,sample)*bps/10000L);if(count>0)cargo.addAll(Arrays.asList(StockMath.take(work,sample,count,0)));}
        var next=new RaidStock(id,defender,attacker,actor,bps,cargo.toArray(ItemStack[]::new),"RESERVED");
        a.write(defender,work);a.put(id,next);
        try{a.commit();}catch(IOException|RuntimeException error){a.write(defender,before);a.put(id,null);throw error;}
        return next;
    }
    public static String settle(Access a,UUID id,boolean deliver)throws IOException {
        var old=a.get(id);if(old==null)return "MISSING";if(!old.state().equals("RESERVED"))return old.state();
        UUID target=deliver?old.attacker():old.defender();var before=a.read(target);var work=StockMath.copy(before);
        if(!StockMath.insert(work,old.cargo()))return "FULL";
        a.write(target,work);a.put(id,old.finish(deliver));
        try{a.commit();}catch(IOException|RuntimeException error){a.write(target,before);a.put(id,old);throw error;}
        return deliver?"DELIVERED":"RETURNED";
    }
}
