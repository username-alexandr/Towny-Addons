package ru.neverland.townybuilds.storage;
import java.util.*;
import java.io.IOException;
import org.bukkit.inventory.ItemStack;
import ru.neverland.townybuilds.api.TradeCargo;
import ru.neverland.townybuilds.data.TownData;
/** Main-thread caller; no partial batches and no acknowledgement until the caller journal is terminal. */
public final class TradeStorageTransactions {
    public interface Access {ItemStack[] read(UUID town);void write(UUID town,ItemStack[] items);boolean busy(UUID town);void commit()throws IOException;}
    private TradeStorageTransactions(){}
    public static String reserve(TownData seller,Access access,UUID buyer,UUID id,ItemStack sample,int amount)throws IOException {
        if(seller.townId().equals(buyer)||!StockMath.present(sample)||amount<1||amount>3456)throw new IllegalArgumentException("Условия поставки");
        var old=seller.tradeCargo().get(id);
        if(old!=null){if(!old.buyer().equals(buyer)||old.amount()!=amount||!old.sample().isSimilar(sample))throw new IllegalArgumentException("ID поставки занят");return old.status();}
        if(access.busy(seller.townId())||access.busy(buyer))return "BUSY";
        var before=access.read(seller.townId());var work=StockMath.copy(before);
        if(StockMath.count(work,sample)<amount)return "STOCK_LOW";
        var cargo=StockMath.take(work,sample,amount,0);
        if(!StockMath.insert(access.read(buyer),cargo))return "FULL";
        var receipt=new TradeCargo(id,buyer,sample,amount,cargo,"RESERVED");
        access.write(seller.townId(),work);seller.putTradeCargo(receipt);
        try{access.commit();}catch(IOException|RuntimeException ex){access.write(seller.townId(),before);seller.removeTradeCargo(id);throw ex;}
        return "RESERVED";
    }
    public static String settle(TownData seller,Access access,UUID id,boolean deliver)throws IOException {
        var old=seller.tradeCargo().get(id);if(old==null)return "MISSING";
        if(!old.status().equals("RESERVED"))return old.status();
        UUID target=deliver?old.buyer():seller.townId();if(access.busy(target))return "BUSY";
        var before=access.read(target);var work=StockMath.copy(before);if(!StockMath.insert(work,old.cargo()))return "FULL";
        access.write(target,work);seller.putTradeCargo(old.finish(deliver));
        try{access.commit();}catch(IOException|RuntimeException ex){access.write(target,before);seller.putTradeCargo(old);throw ex;}
        return deliver?"DELIVERED":"RETURNED";
    }
    public static void acknowledge(TownData seller,Access access,UUID id)throws IOException {
        var old=seller.tradeCargo().get(id);if(old==null)return;if(old.status().equals("RESERVED"))throw new IllegalArgumentException("Поставка ещё зарезервирована");
        seller.removeTradeCargo(id);try{access.commit();}catch(IOException|RuntimeException ex){seller.putTradeCargo(old);throw ex;}
    }
}
