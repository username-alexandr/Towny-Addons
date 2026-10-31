package cc.crafto.ui.server;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import java.util.*;

/** Optional in-memory Towny lookup; no hard dependency or disk/player-region traversal. */
final class CommandSuggestions {
    static List<String> residents(Server server,Player actor){
        var owner=server.getPluginManager().getPlugin("Towny");if(owner==null||!owner.isEnabled())return List.of();
        try{
            Class<?> apiClass=Class.forName("com.palmergames.bukkit.towny.TownyAPI",true,owner.getClass().getClassLoader());
            Object api=apiClass.getMethod("getInstance").invoke(null),resident=apiClass.getMethod("getResident",UUID.class).invoke(api,actor.getUniqueId());
            if(resident==null)return List.of();Object town=resident.getClass().getMethod("getTownOrNull").invoke(resident);if(town==null)return List.of();
            Object value=town.getClass().getMethod("getResidents").invoke(town);if(!(value instanceof Collection<?> residents))return List.of();
            var names=new ArrayList<String>();for(Object r:residents){if(names.size()>=1000)break;names.add((String)r.getClass().getMethod("getName").invoke(r));}return names;
        }catch(ReflectiveOperationException|LinkageError e){return List.of();}
    }
}
