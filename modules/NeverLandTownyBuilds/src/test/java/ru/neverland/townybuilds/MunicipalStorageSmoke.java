package ru.neverland.townybuilds;
import java.util.*;
import org.bukkit.inventory.ItemStack;
import ru.neverland.townybuilds.data.TownData;
import ru.neverland.townybuilds.storage.*;
import static ru.neverland.townybuilds.MarketStorageSmoke.*;
public final class MunicipalStorageSmoke {
    public static void main(String[] args)throws Exception{
        UUID city=UUID.randomUUID(),id=UUID.randomUUID();TownData town=new TownData(city,54);Access a=new Access();a.items.put(city,new ItemStack[54]);Item sample=new Item("stone",1,64),custom=new Item("ia:stone",1,64);
        a.busy=true;check(MunicipalDeposits.deposit(town,a,id,sample,1000).equals("BUSY"),"editor lock");a.busy=false;a.fail=true;failure(()->MunicipalDeposits.deposit(town,a,id,sample,1000));check(StockMath.total(a.read(city))==0&&town.municipalReceipts().isEmpty(),"failed save rolls inventory and receipt back");a.fail=false;
        check(MunicipalDeposits.deposit(town,a,id,sample,1000).equals("DELIVERED"),"1000 stone delivered");MunicipalDeposits.deposit(town,a,id,sample,1000);check(StockMath.total(a.read(city))==1000,"replay never duplicates stock");check(Arrays.stream(a.read(city)).filter(Objects::nonNull).allMatch(i->i.getAmount()<=64),"cargo split into legal stacks");failure(()->MunicipalDeposits.deposit(town,a,id,custom,1000));failure(()->MunicipalDeposits.deposit(town,a,id,sample,999));
        TownData restored=new TownData(city,54);town.municipalReceipts().values().forEach(restored::putMunicipalReceipt);MunicipalDeposits.deposit(restored,a,id,sample,1000);check(StockMath.total(a.read(city))==1000,"receipt survives restored data");a.fail=true;failure(()->MunicipalDeposits.acknowledge(restored,a,id));check(restored.municipalReceipts().containsKey(id),"failed acknowledgement keeps receipt");a.fail=false;MunicipalDeposits.acknowledge(restored,a,id);check(restored.municipalReceipts().isEmpty()&&StockMath.total(a.read(city))==1000,"ack removes only receipt");
        UUID second=UUID.randomUUID();check(MunicipalDeposits.deposit(restored,a,second,custom,3000).equals("FULL"),"all-or-nothing capacity check");check(StockMath.total(a.read(city))==1000&&!restored.municipalReceipts().containsKey(second),"full storage loses no cargo");
        System.out.println("MunicipalStorageSmoke OK: 1000-item delivery, metadata, replay, durable receipt, locks, capacity and save rollback");
    }
}
