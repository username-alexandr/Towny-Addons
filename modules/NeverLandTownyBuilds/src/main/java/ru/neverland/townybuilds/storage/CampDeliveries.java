package ru.neverland.townybuilds.storage;
import java.util.*;
import java.io.IOException;
import org.bukkit.inventory.ItemStack;
import ru.neverland.townybuilds.data.TownData;
/** Cross-ledger hand-off: destination credit precedes source terminal receipt, and blocks refund. */
public final class CampDeliveries {
    public interface Receiver{boolean received(UUID town,UUID id,UUID camp,ItemStack[] cargo)throws Exception;boolean receive(UUID town,UUID id,UUID camp,ItemStack[] cargo)throws Exception;}
    private CampDeliveries(){}
    public static boolean camp(String id){return id.startsWith("raid_camp:");}
    public static UUID id(String id){if(!camp(id))throw new IllegalArgumentException("Не склад лагеря");return UUID.fromString(id.substring("raid_camp:".length()));}
    public static boolean unload(UUID town,TownData data,ShipmentTransactions.Access access,Receiver receiver,UUID id,boolean returned)throws IOException{
        var shipment=data.shipments().get(id);if(shipment==null)return false;if(!shipment.inTransit())return true;UUID camp=id(shipment.target());
        try{
            boolean credited=receiver.received(town,id,camp,shipment.cargo());
            if(!credited&&returned)return ShipmentTransactions.unload(data,access,id,true);
            if(!credited&&!receiver.receive(town,id,camp,shipment.cargo()))return false;
            data.putShipment(shipment.finish(false));try{access.commit();}catch(IOException|RuntimeException failure){data.putShipment(shipment);throw failure;}return true;
        }catch(IOException ex){throw ex;}catch(Exception ex){throw new IOException("Груз лагеря сохранён в пути до восстановления связи",ex);}
    }
}
