package cc.crafto.ui.server;
import java.util.*;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import static cc.crafto.ui.Protocol.*;
/** Optional, capability-negotiated display data. Never accepts a project or placement from the client. */
final class BuildingPreviewBridge {
    static boolean available(CraftoUIPlugin plugin){
        var builds=plugin.getServer().getPluginManager().getPlugin("NeverLandTownyBuilds");
        if(builds==null||!builds.isEnabled())return false;
        try{builds.getClass().getMethod("buildingPreview",Player.class,Inventory.class);return true;}catch(ReflectiveOperationException missing){return false;}
    }
    static void load(CraftoUIPlugin plugin,Player player,Inventory inventory,String key){
        if(key.isEmpty())return;
        var builds=plugin.getServer().getPluginManager().getPlugin("NeverLandTownyBuilds");if(builds==null||!builds.isEnabled())return;
        try{
            Object value=builds.getClass().getMethod("buildingPreview",Player.class,Inventory.class).invoke(builds,player,inventory);
            if(!(value instanceof CompletionStage<?> future))return;
            future.whenComplete((result,error)->{
                if(!plugin.isEnabled())return;
                player.getScheduler().run(plugin,t->{
                    if(!player.isOnline()||player.getOpenInventory().getTopInventory()!=inventory||!plugin.hasBuildingPreview(player.getUniqueId())||!player.hasPermission("craftoui.use")||!player.hasPermission("neverlandtownybuilds.use"))return;
                    if(error!=null||!(result instanceof Map<?,?> images)||images.isEmpty()){
                        plugin.buildingPreview(player,object("key",key,"available",false));return;
                    }
                    for(int view=0;view<4;view++){
                        Object raw=images.get(Integer.toString(view));if(!(raw instanceof String encoded)||encoded.length()>22000)continue;
                        var chunks=new ArrayList<String>();for(int i=0;i<encoded.length();i+=4000)chunks.add(encoded.substring(i,Math.min(encoded.length(),i+4000)));
                        plugin.buildingPreview(player,object("key",key,"view",view,"chunks",chunks));
                    }
                },null);
            });
        }catch(ReflectiveOperationException|RuntimeException ignored){/* Older Builds has no thumbnail API; ordinary native menu stays usable. */}
    }
}
