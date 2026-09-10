package ru.neverland.townybuilds.api;
import java.util.*;
import org.bukkit.inventory.ItemStack;
import ru.neverland.townybuilds.storage.StockMath;
/** Exact intercity consignment. Receipt and both warehouses share one atomic snapshot. */
public record TradeCargo(UUID id,UUID buyer,ItemStack sample,int amount,ItemStack[] cargo,String status) {
    public TradeCargo {
        if(id==null||buyer==null||!StockMath.present(sample)||amount<1||amount>3456
                ||!Set.of("RESERVED","DELIVERED","RETURNED").contains(status))throw new IllegalArgumentException("Некорректная поставка");
        sample=sample.clone();sample.setAmount(1);cargo=StockMath.copy(cargo);
        if(status.equals("RESERVED")?(StockMath.total(cargo)!=amount||StockMath.count(cargo,sample)!=amount):StockMath.total(cargo)!=0)
            throw new IllegalArgumentException("Повреждён резерв поставки");
    }
    @Override public ItemStack sample(){return sample.clone();}
    @Override public ItemStack[] cargo(){return StockMath.copy(cargo);}
    public TradeCargo finish(boolean delivered){return new TradeCargo(id,buyer,sample,amount,new ItemStack[0],delivered?"DELIVERED":"RETURNED");}
}
