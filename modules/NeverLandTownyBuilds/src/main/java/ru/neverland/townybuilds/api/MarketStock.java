package ru.neverland.townybuilds.api;
import java.util.*;
import org.bukkit.inventory.ItemStack;
import ru.neverland.townybuilds.storage.StockMath;
/** Physical market escrow; no item exists simultaneously in a city warehouse and this ledger. */
public record MarketStock(UUID id,ItemStack sample,int total,int available,boolean open,Map<UUID,Hold> holds) {
    public record Hold(UUID id,UUID buyer,boolean city,int amount,String status){
        public Hold {if(id==null||buyer==null||amount<1||amount>3456||!Set.of("HELD","DELIVERED","RETURNED","PICKUP","CLAIM_PENDING","CLAIMED").contains(status)
                ||city&&Set.of("PICKUP","CLAIM_PENDING","CLAIMED").contains(status)||!city&&status.equals("DELIVERED"))throw new IllegalArgumentException("Повреждён резерв покупки");}
        public Hold status(String s){return new Hold(id,buyer,city,amount,s);}
    }
    public MarketStock {
        if(id==null||!StockMath.present(sample)||total<1||total>3456||available<0||available>total||!open&&available!=0)throw new IllegalArgumentException("Повреждён товар рынка");
        sample=sample.clone();sample.setAmount(1);holds=Map.copyOf(holds);
        long held=0;for(var entry:holds.entrySet()){if(!entry.getKey().equals(entry.getValue().id()))throw new IllegalArgumentException("ID покупки не совпадает");if(!entry.getValue().status().equals("RETURNED"))held+=entry.getValue().amount();}
        if(held+available>total)throw new IllegalArgumentException("Количество товара превышает исходный резерв");
    }
    @Override public ItemStack sample(){return sample.clone();}
    public ItemStack[] cargo(int count){List<ItemStack> items=new ArrayList<>();int max=Math.max(1,Math.min(64,sample.getMaxStackSize()));while(count>0){var item=sample();int n=Math.min(count,max);item.setAmount(n);items.add(item);count-=n;}return items.toArray(ItemStack[]::new);}
    public MarketStock change(int count,boolean active,Hold hold){var next=new HashMap<>(holds);if(hold!=null)next.put(hold.id(),hold);return new MarketStock(id,sample,total,count,active,next);}
    public MarketStock remove(UUID order){var next=new HashMap<>(holds);next.remove(order);return new MarketStock(id,sample,total,available,open,next);}
}
