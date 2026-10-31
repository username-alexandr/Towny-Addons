package cc.crafto.ui.server;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.UUID;
import static cc.crafto.ui.server.PublicMethods.*;

/** In-memory Towny membership; call from the player's EntityScheduler. No economy or world reads. */
final class TownMembership {
    static boolean hasTown(Plugin owner,Player player){
        Plugin towny=owner.getServer().getPluginManager().getPlugin("Towny");
        if(towny==null||!towny.isEnabled())return false;
        try{
            Object api=staticCall(towny.getClass().getClassLoader(),"com.palmergames.bukkit.towny.TownyAPI","getInstance");
            Object resident=call(api,"getResident",UUID.class,player.getUniqueId());
            return resident!=null&&call(resident,"getTownOrNull")!=null;
        }catch(Exception|LinkageError unavailable){return false;}
    }
}
