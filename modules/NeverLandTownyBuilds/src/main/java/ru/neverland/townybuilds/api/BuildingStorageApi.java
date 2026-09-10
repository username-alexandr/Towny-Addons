package ru.neverland.townybuilds.api;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.io.IOException;
/** Main-thread API. Idempotent pickup and unload; terminal receipts remain until acknowledged. */
public interface BuildingStorageApi {
    Map<String,BuildingDepot> depots(UUID town);
    ItemStack[] stock(UUID town,String project);
    Map<UUID,CargoShipment> shipments(UUID town);
    Set<UUID> shipmentTowns();
    CargoShipment pickup(UUID town,UUID id,String route,String source,String target,ItemStack filter,int limit,int keep) throws IOException;
    boolean unload(UUID town,UUID id,boolean returnToSource) throws IOException;
    void acknowledge(UUID town,UUID id) throws IOException;
    /** Since Builds 0.8.7. Status strings avoid a compile dependency for optional integrations. */
    default String reserveTrade(UUID seller,UUID buyer,UUID id,ItemStack sample,int amount)throws IOException{return "UNAVAILABLE";}
    default String settleTrade(UUID seller,UUID id,boolean deliver)throws IOException{return "UNAVAILABLE";}
    default void acknowledgeTrade(UUID seller,UUID id)throws IOException{throw new IOException("API поставок недоступно");}
    void openStorage(Player player,String project);
}
