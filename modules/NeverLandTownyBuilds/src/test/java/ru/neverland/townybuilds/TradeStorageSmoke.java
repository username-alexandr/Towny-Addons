package ru.neverland.townybuilds;
import java.util.*;
import java.io.IOException;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.neverland.townybuilds.data.TownData;
import ru.neverland.townybuilds.storage.*;
public final class TradeStorageSmoke {
    static final class Item extends ItemStack {
        final String key;int amount;Item(String key,int amount){super();this.key=key;this.amount=amount;}
        public Material getType(){return Material.IRON_INGOT;}public int getAmount(){return amount;}public void setAmount(int n){amount=n;}public int getMaxStackSize(){return 64;}
        public Item clone(){return new Item(key,amount);}public boolean isSimilar(ItemStack o){return o instanceof Item i&&key.equals(i.key);}
    }
    static final class Access implements TradeStorageTransactions.Access {
        final Map<UUID,ItemStack[]> stock=new HashMap<>();boolean busy,fail;int commits;
        public ItemStack[] read(UUID town){return StockMath.copy(stock.get(town));}public void write(UUID town,ItemStack[] items){stock.put(town,StockMath.copy(items));}
        public boolean busy(UUID town){return busy;}public void commit()throws IOException{if(fail)throw new IOException("full disk");commits++;}
    }
    public static void main(String[] args)throws Exception{
        UUID seller=UUID.randomUUID(),buyer=UUID.randomUUID(),id=UUID.randomUUID();var town=new TownData(seller,54);var a=new Access();var iron=new Item("iron",1);var custom=new Item("ia:iron",1);
        var stock=new ItemStack[54];for(int i=0;i<8;i++)stock[i]=new Item("iron",64);stock[9]=new Item("ia:iron",32);a.stock.put(seller,stock);a.stock.put(buyer,new ItemStack[54]);
        a.busy=true;check(TradeStorageTransactions.reserve(town,a,buyer,id,iron,500).equals("BUSY"),"shared editor lock blocks changes");a.busy=false;
        a.fail=true;failure(()->TradeStorageTransactions.reserve(town,a,buyer,id,iron,500));check(StockMath.count(a.read(seller),iron)==512&&town.tradeCargo().isEmpty(),"reservation rollback restores stock and receipt");a.fail=false;
        check(TradeStorageTransactions.reserve(town,a,buyer,id,iron,500).equals("RESERVED"),"exact batch reserved");check(StockMath.count(a.read(seller),iron)==12&&StockMath.count(a.read(seller),custom)==32,"vanilla never consumes custom metadata");
        int count=a.commits;TradeStorageTransactions.reserve(town,a,buyer,id,iron,500);check(a.commits==count,"repeated reservation not withdrawn twice");
        failure(()->TradeStorageTransactions.reserve(town,a,buyer,id,iron,499));failure(()->TradeStorageTransactions.reserve(town,a,UUID.randomUUID(),id,iron,500));failure(()->TradeStorageTransactions.reserve(town,a,buyer,id,custom,500));
        var restart=new TownData(seller,54);town.tradeCargo().values().forEach(restart::putTradeCargo);a.fail=true;failure(()->TradeStorageTransactions.settle(restart,a,id,true));check(StockMath.total(a.read(buyer))==0&&restart.tradeCargo().get(id).status().equals("RESERVED"),"failed delivery does not lose cargo");a.fail=false;
        var full=new ItemStack[54];Arrays.setAll(full,i->new Item("other",64));a.stock.put(buyer,full);check(TradeStorageTransactions.settle(restart,a,id,true).equals("FULL"),"full buyer retains entire reserve");a.stock.put(buyer,new ItemStack[54]);
        TradeStorageTransactions.settle(restart,a,id,true);TradeStorageTransactions.settle(restart,a,id,true);check(StockMath.count(a.read(buyer),iron)==500,"replayed delivery adds exactly 500");
        check(TradeStorageTransactions.settle(restart,a,id,false).equals("DELIVERED")&&StockMath.count(a.read(seller),iron)==12,"terminal receipt cannot be returned after delivery");
        TradeStorageTransactions.acknowledge(restart,a,id);TradeStorageTransactions.acknowledge(restart,a,id);check(restart.tradeCargo().isEmpty(),"acknowledge is idempotent");
        UUID second=UUID.randomUUID();check(TradeStorageTransactions.reserve(restart,a,buyer,second,iron,500).equals("STOCK_LOW"),"no partial supply when stock insufficient");
        TradeStorageTransactions.reserve(restart,a,buyer,second,custom,32);failure(()->TradeStorageTransactions.acknowledge(restart,a,second));TradeStorageTransactions.settle(restart,a,second,false);TradeStorageTransactions.settle(restart,a,second,false);
        check(StockMath.count(a.read(seller),custom)==32&&StockMath.count(a.read(buyer),custom)==0,"refund preserves exact custom items once");
        System.out.println("TradeStorageSmoke OK: 500-item intercity reserve, atomic rollback, restart replay, metadata, lock, capacity, terminal receipt and return conservation");
    }
    interface Action{void run()throws Exception;}static void failure(Action a)throws Exception{boolean failed=false;try{a.run();}catch(Exception ex){failed=true;}check(failed,"expected rejection");}
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
