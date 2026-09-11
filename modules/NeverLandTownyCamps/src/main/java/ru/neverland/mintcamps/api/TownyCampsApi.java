package ru.neverland.mintcamps.api;
import java.util.*;import org.bukkit.inventory.ItemStack;
/** Detached snapshot and atomic stash mutation. Caller is responsible for gameplay authorization. Server thread only. */
public interface TownyCampsApi extends ru.neverland.core.ApiContract {
    default Set<String> capabilities(){return Set.of("camp","stashBusy","consume");}
    Map<String,Object> camp(UUID owner);
    boolean stashBusy(UUID owner);
    boolean consume(UUID owner,ItemStack[] costs)throws java.io.IOException;
}
