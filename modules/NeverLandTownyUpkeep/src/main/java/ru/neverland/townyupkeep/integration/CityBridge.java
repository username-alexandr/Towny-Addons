package ru.neverland.townyupkeep.integration;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import org.bukkit.*;
import ru.neverland.townyupkeep.model.PaymentProcessor.Gateway;
import java.util.*;
import java.lang.reflect.*;
public final class CityBridge implements Gateway {
    private static Class<?> api(String pluginName,String name)throws Exception{
        var plugin=Bukkit.getPluginManager().getPlugin(pluginName);if(plugin==null||!plugin.isEnabled())throw new IllegalStateException(pluginName+" недоступен");return Class.forName(name,true,plugin.getClass().getClassLoader());
    }
    private Object resource(String method,Class<?>[] types,Object... args)throws Exception{
        Class<?> api=api("NeverLandTownyResources","ru.neverland.townyresources.api.TownyResourcesApi");Object provider=Bukkit.getServicesManager().load(api);if(provider==null)throw new IllegalStateException("API ресурсов недоступен");
        try{return api.getMethod(method,types).invoke(provider,args);}catch(InvocationTargetException ex){if(ex.getCause() instanceof Exception cause)throw cause;throw ex;}
    }
    public void verify()throws Exception{
        api("NeverLandTownyResources","ru.neverland.townyresources.api.TownyResourcesApi").getMethod("reserveResources",UUID.class,UUID.class,Map.class);
        api("NeverLandTownyBuilds","ru.neverland.townybuilds.api.TownyBuildsApi").getMethod("operationalLevel",UUID.class,String.class);
    }
    public Map<String,Integer> buildings(UUID town)throws Exception{
        Class<?> api=api("NeverLandTownyBuilds","ru.neverland.townybuilds.api.TownyBuildsApi");Object provider=Bukkit.getServicesManager().load(api);if(provider==null)throw new IllegalStateException("API построек недоступен");
        Map<?,?> footprints=(Map<?,?>)api.getMethod("buildingFootprints",UUID.class).invoke(provider,town);Map<String,Integer> out=new HashMap<>();
        for(var e:footprints.entrySet()){var b=e.getValue();int level=num(b,"completedLevel");if(level>0&&owned(town,b)&&ru.neverland.integration.SpecializationAccess.allowed(town,e.getKey().toString()))out.put(e.getKey().toString(),Math.min(5,level));}return Map.copyOf(out);
    }
    private static int num(Object o,String method)throws Exception{return ((Number)o.getClass().getMethod(method).invoke(o)).intValue();}
    private boolean owned(UUID town,Object b)throws Exception{
        World world=Bukkit.getWorld((UUID)b.getClass().getMethod("worldId").invoke(b));if(world==null)return false;int size=Coord.getCellSize();if(size<1)return false;
        int x0=Math.floorDiv(num(b,"minX"),size),x1=Math.floorDiv(num(b,"maxX"),size),z0=Math.floorDiv(num(b,"minZ"),size),z1=Math.floorDiv(num(b,"maxZ"),size);
        long width=(long)x1-x0+1,depth=(long)z1-z0+1;if(width<1||depth<1||width>4096||depth>4096||width*depth>4096)return false;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){Town owner=TownyAPI.getInstance().getTown(new Location(world,(double)x*size,world.getMinHeight(),(double)z*size));if(owner==null||!town.equals(owner.getUUID()))return false;}return true;
    }
    @Override public boolean reserve(UUID id,UUID town,Map<String,Long> amounts)throws Exception{return (Boolean)resource("reserveResources",new Class<?>[]{UUID.class,UUID.class,Map.class},id,town,amounts);}
    @Override public void settle(UUID id,boolean consume)throws Exception{
        String status=(String)resource("reservationStatus",new Class<?>[]{UUID.class},id);
        if(!consume&&status.equals("NONE"))return;resource("settleResources",new Class<?>[]{UUID.class,boolean.class},id,consume);
    }
    @Override public boolean canPay(UUID id,long cents){Town town=TownyAPI.getInstance().getTown(id);return town!=null&&TownyEconomyHandler.isActive()&&town.getAccount().canPayFromHoldings(cents/100.0);}
    @Override public boolean withdraw(UUID id,long cents,UUID invoice){Town town=TownyAPI.getInstance().getTown(id);return town!=null&&TownyEconomyHandler.isActive()&&town.getAccount().withdraw(cents/100.0,"NeverLand Upkeep "+invoice);}
    @Override public void forget(UUID id)throws Exception{resource("forgetReservation",new Class<?>[]{UUID.class},id);}
}
