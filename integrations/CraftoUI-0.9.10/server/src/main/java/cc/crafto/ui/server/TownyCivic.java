package cc.crafto.ui.server;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.*;
import static cc.crafto.ui.server.PublicMethods.*;
/** Public Towny API only. Capture player/inventory exclusively from the owning entity task. */
final class TownyCivic {
    record Context(UUID player,String name,UUID town,String townName,boolean manager,String roles,UUID world,int x,int z,int size,int claimed,int remaining){}
    private final CraftoUIPlugin plugin;
    TownyCivic(CraftoUIPlugin plugin){this.plugin=plugin;}
    Plugin addon(String name){var p=plugin.getServer().getPluginManager().getPlugin(name);return p!=null&&p.isEnabled()?p:null;}
    Object api()throws Exception{var p=addon("Towny");if(p==null)throw new IllegalArgumentException("Towny сейчас недоступен.");return staticCall(p.getClass().getClassLoader(),"com.palmergames.bukkit.towny.TownyAPI","getInstance");}
    Object town(UUID id)throws Exception{return id==null?null:call(api(),"getTown",UUID.class,id);}
    Object resident(UUID id)throws Exception{return call(api(),"getResident",UUID.class,id);}
    Object setting(String method)throws Exception{return staticCall(addon("Towny").getClass().getClassLoader(),"com.palmergames.bukkit.towny.TownySettings",method);}
    Context capture(Player p)throws Exception{
        Object resident=resident(p.getUniqueId()),t=resident==null?null:call(resident,"getTownOrNull");UUID id=t==null?null:(UUID)call(t,"getUUID");
        boolean mayor=t!=null&&p.getUniqueId().equals(call(call(t,"getMayor"),"getUUID"));
        Collection<String> ranks=resident==null?List.of():new TreeSet<>((Collection<String>)call(resident,"getTownRanks"));
        int size=((Number)setting("getTownBlockSize")).intValue();var at=p.getLocation();
        return new Context(p.getUniqueId(),p.getName(),id,t==null?"Нет города":CitySnapshot.clean(call(t,"getName").toString()),mayor||p.hasPermission("craftoui.goals.manage"),(mayor?"Мэр":"Житель")+(ranks.isEmpty()?"":" · "+String.join(", ",ranks)),at.getWorld().getUID(),Math.floorDiv(at.getBlockX(),size),Math.floorDiv(at.getBlockZ(),size),size,t==null?0:((Number)call(t,"getNumTownBlocks")).intValue(),t==null?0:(boolean)call(t,"hasUnlimitedClaims")?-1:Math.max(0,((Number)call(t,"availableTownBlocks")).intValue()));
    }
    /** Economy I/O is permitted only on the async path and only by Towny's async economy setting. */
    boolean economy()throws Exception{return (boolean)staticCall(addon("Towny").getClass().getClassLoader(),"com.palmergames.bukkit.towny.TownyEconomyHandler","isActive");}
    String balance(Context c){
        if(c.town()==null)return "—";
        try{
            ClassLoader loader=addon("Towny").getClass().getClassLoader();
            if(!(boolean)staticCall(loader,"com.palmergames.bukkit.towny.TownyEconomyHandler","isActive"))return "Экономика недоступна";
            if(!(boolean)setting("isEconomyAsync"))return "Баланс доступен в меню казны";
            double amount=((Number)call(call(town(c.town()),"getAccount"),"getHoldingBalance")).doubleValue();
            return Double.isFinite(amount)?String.format(Locale.ROOT,"%.2f",amount):"Недоступно";
        }catch(Exception e){return "Недоступно";}
    }
    Object plot(Context c,int x,int z)throws Exception{
        ClassLoader loader=addon("Towny").getClass().getClassLoader();Class<?> type=Class.forName("com.palmergames.bukkit.towny.object.WorldCoord",true,loader);
        World w=Bukkit.getWorld(c.world());if(w==null)throw new IllegalArgumentException("Мир недоступен.");
        Object coord=type.getConstructor(String.class,int.class,int.class).newInstance(w.getName(),x,z);
        return api().getClass().getMethod("getTownBlock",type).invoke(api(),coord);
    }
    String residentName(UUID id){try{Object r=resident(id);return r==null?id.toString().substring(0,8):call(r,"getName").toString();}catch(Exception e){return id.toString().substring(0,8);}}
    record Invite(String id,String town,String commandName){}
    List<Invite> invitations(Player p)throws Exception{
        Object r=resident(p.getUniqueId());if(r==null)return List.of();List<Invite> out=new ArrayList<>();
        for(Object invite:new ArrayList<>((Collection<?>)call(r,"getReceivedInvites"))){
            if(!invite.getClass().getName().equals("com.palmergames.bukkit.towny.object.inviteobjects.PlayerJoinTownInvite"))continue;
            Object t=call(invite,"getSender");String name=call(t,"getName").toString();out.add(new Invite(call(t,"getUUID").toString(),CitySnapshot.clean(name),name));
        }return List.copyOf(out);
    }
}
