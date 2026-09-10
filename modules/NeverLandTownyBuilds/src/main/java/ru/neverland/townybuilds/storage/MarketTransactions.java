package ru.neverland.townybuilds.storage;
import java.util.*;
import java.io.IOException;
import org.bukkit.inventory.ItemStack;
import ru.neverland.townybuilds.api.MarketStock;
import ru.neverland.townybuilds.data.TownData;
public final class MarketTransactions {
    public interface Access {ItemStack[] read(UUID town);void write(UUID town,ItemStack[] items);boolean busy(UUID town);void commit()throws IOException;}
    public interface Inventory {UUID owner();ItemStack[] read();void write(ItemStack[] items);void save();}
    private MarketTransactions(){}
    private static void update(TownData town,Access a,MarketStock next)throws IOException {
        var old=town.marketStock().get(next.id());town.putMarketStock(next);
        try{a.commit();}catch(IOException|RuntimeException ex){if(old==null)town.removeMarketStock(next.id());else town.putMarketStock(old);throw ex;}
    }
    public static String open(TownData town,Access a,UUID lot,ItemStack sample,int amount)throws IOException {
        var old=town.marketStock().get(lot);if(old!=null){if(old.total()!=amount||!old.sample().isSimilar(sample))throw new IllegalArgumentException("ID предложения занят");return old.open()?"OPEN":"CLOSED";}
        var next=new MarketStock(lot,sample,amount,amount,true,Map.of());if(a.busy(town.townId()))return "BUSY";
        var before=a.read(town.townId());var work=StockMath.copy(before);if(StockMath.count(work,sample)<amount)return "STOCK_LOW";
        StockMath.take(work,sample,amount,0);a.write(town.townId(),work);
        try{update(town,a,next);}catch(IOException|RuntimeException ex){a.write(town.townId(),before);throw ex;}return "OPEN";
    }
    public static String reserve(TownData town,Access a,UUID lot,UUID order,UUID buyer,boolean city,int amount)throws IOException {
        var stock=town.marketStock().get(lot);if(stock==null)return "MISSING";
        var old=stock.holds().get(order);if(old!=null){if(!old.buyer().equals(buyer)||old.city()!=city||old.amount()!=amount)throw new IllegalArgumentException("ID покупки занят");return old.status();}
        var hold=new MarketStock.Hold(order,buyer,city,amount,"HELD");if(city&&buyer.equals(town.townId()))throw new IllegalArgumentException("Покупка у своего города");
        if(!stock.open())return "CLOSED";if(stock.available()<amount)return "STOCK_LOW";
        if(city&&(a.busy(buyer)||!StockMath.insert(a.read(buyer),stock.cargo(amount))))return "FULL";
        update(town,a,stock.change(stock.available()-amount,true,hold));return "HELD";
    }
    public static String deliver(TownData town,Access a,UUID lot,UUID order)throws IOException {
        var stock=town.marketStock().get(lot);var hold=stock==null?null:stock.holds().get(order);if(hold==null)return "MISSING";
        if(!hold.status().equals("HELD"))return hold.status();
        if(!hold.city()){update(town,a,stock.change(stock.available(),stock.open(),hold.status("PICKUP")));return "PICKUP";}
        if(a.busy(hold.buyer()))return "BUSY";var before=a.read(hold.buyer());var work=StockMath.copy(before);if(!StockMath.insert(work,stock.cargo(hold.amount())))return "FULL";
        a.write(hold.buyer(),work);try{update(town,a,stock.change(stock.available(),stock.open(),hold.status("DELIVERED")));}
        catch(IOException|RuntimeException ex){a.write(hold.buyer(),before);throw ex;}return "DELIVERED";
    }
    public static String refund(TownData town,Access a,UUID lot,UUID order)throws IOException {
        var stock=town.marketStock().get(lot);var hold=stock==null?null:stock.holds().get(order);if(hold==null)return "MISSING";
        if(!hold.status().equals("HELD"))return hold.status();
        if(stock.open()){update(town,a,stock.change(stock.available()+hold.amount(),true,hold.status("RETURNED")));return "RETURNED";}
        if(a.busy(town.townId()))return "BUSY";var before=a.read(town.townId());var work=StockMath.copy(before);if(!StockMath.insert(work,stock.cargo(hold.amount())))return "FULL";
        a.write(town.townId(),work);try{update(town,a,stock.change(0,false,hold.status("RETURNED")));}catch(IOException|RuntimeException ex){a.write(town.townId(),before);throw ex;}return "RETURNED";
    }
    public static String close(TownData town,Access a,UUID lot)throws IOException {
        var stock=town.marketStock().get(lot);if(stock==null)return "MISSING";if(!stock.open())return "CLOSED";
        if(stock.available()==0){update(town,a,stock.change(0,false,null));return "CLOSED";}
        if(a.busy(town.townId()))return "BUSY";var before=a.read(town.townId());var work=StockMath.copy(before);if(!StockMath.insert(work,stock.cargo(stock.available())))return "FULL";
        a.write(town.townId(),work);try{update(town,a,stock.change(0,false,null));}catch(IOException|RuntimeException ex){a.write(town.townId(),before);throw ex;}return "CLOSED";
    }
    public static void acknowledge(TownData town,Access a,UUID lot,UUID order)throws IOException {
        var stock=town.marketStock().get(lot);var hold=stock==null?null:stock.holds().get(order);if(hold==null)return;
        if(!Set.of("DELIVERED","RETURNED","CLAIMED").contains(hold.status()))throw new IllegalStateException("Покупка ещё не выдана");update(town,a,stock.remove(order));
    }
    public static void forget(TownData town,Access a,UUID lot)throws IOException {
        var stock=town.marketStock().get(lot);if(stock==null)return;if(stock.open()||stock.available()>0||!stock.holds().isEmpty())throw new IllegalStateException("Резерв предложения ещё нужен");
        town.removeMarketStock(lot);try{a.commit();}catch(IOException|RuntimeException ex){town.putMarketStock(stock);throw ex;}
    }
    /** Player data and town data cannot share a transaction. Persist intent and never replay an uncertain handoff. */
    public static String claim(TownData town,Access a,UUID lot,UUID order,Inventory inventory)throws IOException {
        var stock=town.marketStock().get(lot);var hold=stock==null?null:stock.holds().get(order);if(hold==null)return "MISSING";
        if(hold.city()||!hold.buyer().equals(inventory.owner()))throw new IllegalArgumentException("Чужая покупка");if(!hold.status().equals("PICKUP"))return hold.status();
        var work=StockMath.copy(inventory.read());if(!StockMath.insert(work,stock.cargo(hold.amount())))return "FULL";
        var pending=stock.change(stock.available(),stock.open(),hold.status("CLAIM_PENDING"));update(town,a,pending);
        inventory.write(work);inventory.save();
        update(town,a,pending.change(pending.available(),pending.open(),hold.status("CLAIMED")));return "CLAIMED";
    }
    public static void resolveClaim(TownData town,Access a,UUID lot,UUID order,boolean received)throws IOException {
        var stock=town.marketStock().get(lot);var hold=stock==null?null:stock.holds().get(order);
        if(hold==null||!hold.status().equals("CLAIM_PENDING"))throw new IllegalArgumentException("Нет выдачи, ожидающей сверки");
        update(town,a,stock.change(stock.available(),stock.open(),hold.status(received?"CLAIMED":"PICKUP")));
    }
}
