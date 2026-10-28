package ru.neverland.townysieges.api;
import java.util.*;
import org.bukkit.inventory.ItemStack;
public interface SiegesCampSupplyApi extends ru.neverland.core.ApiContract {
    default Set<String> capabilities(){return Set.of("depot","stock","receive","received","hasReceipt","allowsTransit","open");}
    Map<String,Object> depot(UUID town);
    ItemStack[] stock(UUID town);
    boolean receive(UUID town,UUID shipment,UUID camp,ItemStack[] cargo)throws Exception;
    boolean hasReceipt(UUID town,UUID shipment);
    boolean received(UUID town,UUID shipment,UUID camp,ItemStack[] cargo)throws Exception;
    boolean allowsTransit(UUID town,UUID world,double x,double y,double z);
    void open(org.bukkit.entity.Player player);
}
