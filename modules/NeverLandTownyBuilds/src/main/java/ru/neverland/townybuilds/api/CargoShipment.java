package ru.neverland.townybuilds.api;
import org.bukkit.inventory.ItemStack;
import java.util.UUID;
import ru.neverland.townybuilds.storage.StockMath;
/** Cargo and its source/destination changes are persisted in the same atomic snapshot. */
public record CargoShipment(UUID id,String route,String source,String target,ItemStack[] cargo,String status,long createdAt) {
    public CargoShipment {
        if(id==null||route==null||source==null||target==null||source.equals(target)
                ||!java.util.Set.of("TRANSIT","DELIVERED","RETURNED").contains(status))throw new IllegalArgumentException("Некорректный груз");
        cargo=StockMath.copy(cargo);
        if(status.equals("TRANSIT")&&StockMath.total(cargo)==0)throw new IllegalArgumentException("Пустой груз");
        if(!status.equals("TRANSIT")&&StockMath.total(cargo)!=0)throw new IllegalArgumentException("Груз уже разгружен");
    }
    @Override public ItemStack[] cargo(){return StockMath.copy(cargo);}
    public boolean inTransit(){return status.equals("TRANSIT");}
    public CargoShipment finish(boolean returned){return new CargoShipment(id,route,source,target,new ItemStack[0],returned?"RETURNED":"DELIVERED",createdAt);}
}
