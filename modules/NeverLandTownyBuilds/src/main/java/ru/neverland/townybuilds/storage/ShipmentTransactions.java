package ru.neverland.townybuilds.storage;
import ru.neverland.townybuilds.api.CargoShipment;
import ru.neverland.townybuilds.data.TownData;
import org.bukkit.inventory.ItemStack;
import java.util.UUID;
import java.io.IOException;
/** No cargo ever lives in an NPC inventory. Roll back memory when persistence fails. */
public final class ShipmentTransactions {
    public interface Access {ItemStack[] read(String id);void write(String id,ItemStack[] items);boolean busy(String id);void commit()throws IOException;}
    private ShipmentTransactions(){}
    public static CargoShipment pickup(TownData data,Access access,UUID id,String route,String source,String target,ItemStack filter,int limit,int keep)throws IOException{
        CargoShipment old=data.shipments().get(id);
        if(old!=null){if(!old.route().equals(route)||!old.source().equals(source)||!old.target().equals(target))throw new IllegalArgumentException("ID груза уже используется");return old;}
        if(source.equals(target)||limit<1||limit>1024||keep<0)throw new IllegalArgumentException("Параметры груза");
        if(access.busy(source)||access.busy(target))return null;
        var before=access.read(source);var work=StockMath.copy(before);var cargo=StockMath.take(work,filter,limit,keep);
        if(StockMath.total(cargo)==0||!StockMath.insert(access.read(target),cargo))return null;
        var shipment=new CargoShipment(id,route,source,target,cargo,"TRANSIT",System.currentTimeMillis());
        access.write(source,work);data.putShipment(shipment);
        try{access.commit();}catch(IOException|RuntimeException ex){access.write(source,before);data.removeShipment(id);throw ex;}
        return shipment;
    }
    public static boolean unload(TownData data,Access access,UUID id,boolean returned)throws IOException{
        var s=data.shipments().get(id);if(s==null)throw new IllegalArgumentException("Груз не найден");if(!s.inTransit())return true;
        String target=returned?s.source():s.target();if(access.busy(target))return false;
        var before=access.read(target);var work=StockMath.copy(before);if(!StockMath.insert(work,s.cargo()))return false;
        access.write(target,work);data.putShipment(s.finish(returned));
        try{access.commit();}catch(IOException|RuntimeException ex){access.write(target,before);data.putShipment(s);throw ex;}return true;
    }
    public static void acknowledge(TownData data,Access access,UUID id)throws IOException{
        var old=data.shipments().get(id);if(old==null)return;if(old.inTransit())throw new IllegalArgumentException("Груз ещё в пути");
        data.removeShipment(id);try{access.commit();}catch(IOException|RuntimeException ex){data.putShipment(old);throw ex;}
    }
}
