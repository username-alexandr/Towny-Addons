package ru.neverland.townybuilds.api;
import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
/** Builds 0.8.8. Main thread only; caller persists lot/order UUID before first mutation. */
public interface MarketStorageApi {
    int level(UUID town,String project);
    String open(UUID town,UUID lot,ItemStack sample,int amount)throws Exception;
    Map<String,Object> snapshot(UUID town,UUID lot);
    String reserve(UUID town,UUID lot,UUID order,UUID buyer,boolean city,int amount)throws Exception;
    String deliver(UUID town,UUID lot,UUID order)throws Exception;
    String refund(UUID town,UUID lot,UUID order)throws Exception;
    String close(UUID town,UUID lot)throws Exception;
    void acknowledge(UUID town,UUID lot,UUID order)throws Exception;
    void forget(UUID town,UUID lot)throws Exception;
    String claim(Player player,UUID town,UUID lot,UUID order)throws Exception;
    void resolveClaim(UUID town,UUID lot,UUID order,boolean received)throws Exception;
}
