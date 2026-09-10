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
    void openStorage(Player player,String project);
}
