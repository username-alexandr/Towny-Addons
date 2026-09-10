package ru.neverland.townybuilds;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townybuilds.api.CargoShipment;
import ru.neverland.townybuilds.data.TownData;
import ru.neverland.townybuilds.storage.*;
import ru.neverland.townybuilds.util.AtomicYamlFile;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;
public final class ShipmentStorageSmoke {
    private static final class Item extends ItemStack {
        private final String id;private final Material material;private final int max;private int amount;
        Item(String id,Material material,int amount,int max){super();this.id=id;this.material=material;this.amount=amount;this.max=max;}
        @Override public Item clone(){return new Item(id,material,amount,max);}
        @Override public Material getType(){return material;}@Override public int getAmount(){return amount;}@Override public void setAmount(int n){amount=n;}
        @Override public int getMaxStackSize(){return max;}@Override public boolean isSimilar(ItemStack other){return other instanceof Item item&&id.equals(item.id);}
    }
    private static final class Access implements ShipmentTransactions.Access {
        final Map<String,ItemStack[]> stock=new HashMap<>();boolean fail,busy;int commits;
        public ItemStack[] read(String id){return StockMath.copy(stock.get(id));}public void write(String id,ItemStack[] items){stock.put(id,StockMath.copy(items));}
        public boolean busy(String id){return busy;}public void commit()throws IOException{if(fail)throw new IOException("simulated full disk");commits++;}
    }
    public static void main(String[] args)throws Exception{
        var town=new TownData(UUID.randomUUID(),54);var access=new Access();var vanilla=new Item("oak",Material.OAK_LOG,1,64);var custom=new Item("ia:oak",Material.OAK_LOG,1,64);
        access.stock.put("sawmill",new ItemStack[]{new Item("oak",Material.OAK_LOG,40,64),new Item("ia:oak",Material.OAK_LOG,20,64)});access.stock.put("warehouse",new ItemStack[2]);
        UUID id=UUID.randomUUID();var cargo=ShipmentTransactions.pickup(town,access,id,"logs","sawmill","warehouse",vanilla,32,16);
        check(StockMath.total(cargo.cargo())==24&&StockMath.count(access.read("sawmill"),vanilla)==16,"keep threshold respected");
        check(StockMath.count(access.read("sawmill"),custom)==20,"custom metadata not consumed by vanilla filter");
        int commits=access.commits;ShipmentTransactions.pickup(town,access,id,"logs","sawmill","warehouse",vanilla,32,0);check(access.commits==commits,"replayed pickup cannot withdraw twice");
        var mutated=cargo.cargo();mutated[0].setAmount(1);check(StockMath.total(cargo.cargo())==24,"cargo snapshot cannot be mutated by caller");
        var restarted=new TownData(town.townId(),54);town.shipments().values().forEach(restarted::putShipment);town=restarted;
        access.stock.put("warehouse",new ItemStack[]{new Item("stone",Material.STONE,64,64),new Item("stone",Material.STONE,64,64)});
        check(!ShipmentTransactions.unload(town,access,id,false)&&town.shipments().get(id).inTransit(),"full destination retains cargo");
        access.stock.put("warehouse",new ItemStack[2]);access.fail=true;TownData current=town;
        failure(()->ShipmentTransactions.unload(current,access,id,false));check(StockMath.total(access.read("warehouse"))==0&&town.shipments().get(id).inTransit(),"failed unload rolls back both inventory and receipt");
        access.fail=false;check(ShipmentTransactions.unload(town,access,id,false),"delivery succeeds");commits=access.commits;
        check(ShipmentTransactions.unload(town,access,id,false)&&access.commits==commits&&StockMath.total(access.read("warehouse"))==24,"replayed unload cannot duplicate cargo");
        ShipmentTransactions.acknowledge(town,access,id);check(town.shipments().isEmpty(),"terminal receipt acknowledged");
        UUID second=UUID.randomUUID();access.fail=true;var before=StockMath.total(access.read("sawmill"));failure(()->ShipmentTransactions.pickup(current,access,second,"logs","sawmill","warehouse",null,8,0));
        check(StockMath.total(access.read("sawmill"))==before&&!town.shipments().containsKey(second),"failed pickup leaves source untouched");
        access.fail=false;access.busy=true;check(ShipmentTransactions.pickup(town,access,second,"logs","sawmill","warehouse",null,8,0)==null,"open inventory excludes cargo writes");access.busy=false;
        cargo=ShipmentTransactions.pickup(town,access,second,"logs","sawmill","warehouse",custom,8,0);check(cargo.cargo()[0].isSimilar(custom),"custom item filter preserves metadata");
        failure(()->ShipmentTransactions.acknowledge(current,access,second));check(ShipmentTransactions.unload(town,access,second,true),"emergency return uses source");
        check(town.shipments().get(second).status().equals("RETURNED")&&StockMath.count(access.read("sawmill"),custom)==20,"return restores exactly original source quantity");
        check(ShipmentTransactions.unload(town,access,second,true)&&StockMath.count(access.read("sawmill"),custom)==20,"repeated emergency return is idempotent");
        var bucket=new Item("bucket",Material.BUCKET,1,16);var water=new Item("water",Material.WATER_BUCKET,1,1);
        check(StockMath.recipe(new ItemStack[2],List.of(bucket),new ItemStack[]{water}).isEmpty(),"water production requires empty bucket");
        var result=StockMath.recipe(new ItemStack[]{bucket,null},List.of(bucket),new ItemStack[]{water}).orElseThrow();check(StockMath.count(result,bucket)==0&&StockMath.count(result,water)==1,"water exchanges one empty bucket for one filled bucket");
        ItemStack[] limited={new Item("bucket",Material.BUCKET,16,16)};check(StockMath.recipe(limited,List.of(bucket),new ItemStack[]{water}).isEmpty()&&limited[0].getAmount()==16,"full production inventory does not consume inputs");
        ItemStack[] unstackable=new ItemStack[2];check(StockMath.insert(unstackable,new ItemStack[]{new Item("water",Material.WATER_BUCKET,2,1)})&&unstackable[0].getAmount()==1&&unstackable[1].getAmount()==1,"unstackable cargo split to proper slots");
        var sessions=new StorageSessions();UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        check(sessions.acquire(current.townId(),"warehouse",a)&&!sessions.acquire(current.townId(),"warehouse",b),"two players cannot open independent editable warehouse snapshots");
        check(sessions.release(current.townId(),"warehouse",a)&&sessions.acquire(current.townId(),"warehouse",b),"next editor starts after first closes");
        check(!sessions.release(current.townId(),"warehouse",a)&&!sessions.owns(current.townId(),"warehouse",a)&&sessions.owns(current.townId(),"warehouse",b),"late close or mirror cannot unlock or overwrite the newer session");
        persistence();System.out.println("ShipmentStorageSmoke OK: physical cargo ledger, replay, full storage, rollback, custom metadata, locks, returns, recipes and atomic file replacement");
    }
    private static void persistence()throws Exception{Path dir=Files.createTempDirectory("cargo-store-test-");try{Path target=dir.resolve("data.yml");var first=new YamlConfiguration();first.set("source",40);first.set("cargo",0);AtomicYamlFile.write(first,target);
        var second=new YamlConfiguration();second.set("source",16);second.set("cargo",24);AtomicYamlFile.write(second,target);var loaded=new YamlConfiguration();loaded.load(target.toFile());check(loaded.getInt("source")+loaded.getInt("cargo")==40,"atomic persisted source+cargo conservation");
        Path blocked=dir.resolve("blocked");Files.createDirectory(blocked);Files.writeString(blocked.resolve("keep"),"original");failure(()->AtomicYamlFile.write(second,blocked));check(Files.readString(blocked.resolve("keep")).equals("original"),"failed replacement preserves existing data");
    }finally{try(var paths=Files.walk(dir)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}}
    private interface Action{void run()throws Exception;}
    private static void failure(Action action)throws Exception{boolean failed=false;try{action.run();}catch(Exception ex){failed=true;}check(failed,"expected failure");}
    private static void check(boolean condition,String text){if(!condition)throw new AssertionError(text);}
}
