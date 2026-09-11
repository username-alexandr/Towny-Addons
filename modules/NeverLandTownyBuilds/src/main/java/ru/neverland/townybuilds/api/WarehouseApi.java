package ru.neverland.townybuilds.api;
import java.util.*;import org.bukkit.inventory.ItemStack;
/** Atomic legacy one-shot storage operations. Does not promise cross-plugin exactly-once delivery. */
public interface WarehouseApi extends ru.neverland.core.ApiContract {
    default Set<String> capabilities(){return Set.of("take","deposit");}
    Map<String,Object> take(UUID town,ItemStack sample,int amount)throws java.io.IOException;
    Map<String,Object> deposit(UUID town,ItemStack sample,int amount)throws java.io.IOException;
}
