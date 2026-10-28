package ru.neverland.townysieges.api;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.entity.Player;
/** A narrow exception for TNT placement; grants no general building or inventory access. */
public interface TownyWarChunkApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities(){return Set.of("canPlaceTnt");}
    boolean canPlaceTnt(Player actor,Location location);
}
