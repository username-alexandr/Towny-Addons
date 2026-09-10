package ru.neverland.townyresearch.integration;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import org.bukkit.*;
import ru.neverland.townyresearch.model.ResearchProcessor.Gateway;
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
        for(var e:footprints.entrySet()){var b=e.getValue();int level=num(b,"completedLevel");if(level>0&&owned(town,b)&&ru.neverland.integration.BuildingOperations.active(town,e.getKey().toString()))out.put(e.getKey().toString(),Math.min(5,level));}return Map.copyOf(out);
    }
    private static int num(Object o,String method)throws Exception{return ((Number)o.getClass().getMethod(method).invoke(o)).intValue();}
    private boolean owned(UUID town,Object b)throws Exception{
        World world=Bukkit.getWorld((UUID)b.getClass().getMethod("worldId").invoke(b));if(world==null)return false;int size=Coord.getCellSize();if(size<1)return false;
        int x0=Math.floorDiv(num(b,"minX"),size),x1=Math.floorDiv(num(b,"maxX"),size),z0=Math.floorDiv(num(b,"minZ"),size),z1=Math.floorDiv(num(b,"maxZ"),size);
        long width=(long)x1-x0+1,depth=(long)z1-z0+1;if(width<1||depth<1||width>4096||depth>4096||width*depth>4096)return false;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){Town owner=TownyAPI.getInstance().getTown(new Location(world,(double)x*size,world.getMinHeight(),(double)z*size));if(owner==null||!town.equals(owner.getUUID()))return false;}return true;
    }
    @Override public boolean reserve(UUID id,UUID town,long cost)throws Exception{return (Boolean)resource("reserveResources",new Class<?>[]{UUID.class,UUID.class,Map.class},id,town,Map.of("knowledge",cost));}
    @Override public String status(UUID id)throws Exception{return (String)resource("reservationStatus",new Class<?>[]{UUID.class},id);}
    @Override public void settle(UUID id,boolean consume)throws Exception{resource("settleResources",new Class<?>[]{UUID.class,boolean.class},id,consume);}
    @Override public void forget(UUID id)throws Exception{resource("forgetReservation",new Class<?>[]{UUID.class},id);}
    public record Knowledge(long balance,long reserve,boolean paused){}
    public Knowledge knowledge(UUID town)throws Exception{var found=(Optional<?>)resource("resources",new Class<?>[]{UUID.class},town);if(found.isEmpty())throw new IllegalStateException("Ожидается расчёт ресурсов");var view=found.get();var state=view.getClass().getMethod("state").invoke(view);return new Knowledge(knowledgeMap(state,"balances"),knowledgeMap(state,"reserves"),(Boolean)view.getClass().getMethod("paused").invoke(view));}
    private long knowledgeMap(Object state,String method)throws Exception{Map<?,?> amounts=(Map<?,?>)state.getClass().getMethod(method).invoke(state);for(var e:amounts.entrySet())if(e.getKey().toString().equalsIgnoreCase("knowledge"))return ((Number)e.getValue()).longValue();throw new IllegalStateException("Ресурс знаний не найден");}
}
