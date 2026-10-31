package cc.crafto.ui.server;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.luckperms.api.LuckPerms;
import java.util.Map;
final class LuckPermsBridge {
    static void fill(Player player,Map<String,String> data){
        LuckPerms api=Bukkit.getServicesManager().load(LuckPerms.class);
        if(api==null)return;
        var user=api.getUserManager().getUser(player.getUniqueId());
        if(user!=null)data.put("player.group",user.getPrimaryGroup());
    }
}
