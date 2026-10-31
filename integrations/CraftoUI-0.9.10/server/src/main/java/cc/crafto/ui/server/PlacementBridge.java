package cc.crafto.ui.server;
import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.JsonObject;
import static cc.crafto.ui.Protocol.*;
/** Optional Builds API; no arbitrary commands, project IDs, offsets or payment operations. */
final class PlacementBridge {
    private static JavaPlugin builds(CraftoUIPlugin ui,Player player){
        if(!player.hasPermission("craftoui.use")||!player.hasPermission("neverlandtownybuilds.use"))return null;
        var plugin=ui.getServer().getPluginManager().getPlugin("NeverLandTownyBuilds");return plugin instanceof JavaPlugin java&&plugin.isEnabled()?java:null;
    }
    static JsonObject state(CraftoUIPlugin ui,Player player){
        var plugin=builds(ui,player);if(plugin==null)return object("active",false);
        try{
            Object result=plugin.getClass().getMethod("placementControls",Player.class).invoke(plugin,player);
            if(!(result instanceof Map<?,?> m)||!(m.get("key") instanceof String key)||key.length()!=36)return object("active",false);
            return object("active",true,"sequence",Long.parseLong((String)m.get("sequence")),"key",key,"slot",Integer.parseInt((String)m.get("slot")),"x",Integer.parseInt((String)m.get("x")),"y",Integer.parseInt((String)m.get("y")),"z",Integer.parseInt((String)m.get("z")),"fixed",true,"inputMode","menu","anchored",Boolean.parseBoolean((String)m.get("fixed")),"ready",Boolean.parseBoolean((String)m.get("ready")));
        }catch(ReflectiveOperationException|RuntimeException unavailable){return object("active",false);}
    }
    static void move(CraftoUIPlugin ui,Player player,JsonObject message){
        // Legacy arrows/wheel packets never authorize a movement. Native menu clicks are authoritative.
    }
}
