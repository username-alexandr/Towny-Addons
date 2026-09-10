package ru.neverland.townyspecialization.integration;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.*;
import org.bukkit.*;
import ru.neverland.integration.SpecializationRules;
import java.util.*;

public final class CityBridge {
    public Map<String,Integer> levels(UUID town)throws ReflectiveOperationException {
        var plugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds");
        if(plugin==null||!plugin.isEnabled())throw new IllegalStateException("Постройки недоступны");
        Class<?> type=Class.forName("ru.neverland.townybuilds.api.TownyBuildsApi",true,plugin.getClass().getClassLoader());
        Object api=Bukkit.getServicesManager().load(type);if(api==null)throw new IllegalStateException("API построек недоступен");
        Map<?,?> values=(Map<?,?>)type.getMethod("buildingFootprints",UUID.class).invoke(api,town);Map<String,Integer> result=new HashMap<>();
        for(var entry:values.entrySet()){
            Object b=entry.getValue();String id=entry.getKey().toString();int completed=integer(b,"completedLevel");
            if((id.equals("town_hall")||SpecializationRules.PROJECTS.containsKey(id))&&owned(town,b))result.put(id,Math.max(0,Math.min(5,completed)));
        }
        return Map.copyOf(result);
    }
    private static int integer(Object value,String method)throws ReflectiveOperationException{return ((Number)value.getClass().getMethod(method).invoke(value)).intValue();}
    private boolean owned(UUID town,Object b)throws ReflectiveOperationException{
        UUID worldId=(UUID)b.getClass().getMethod("worldId").invoke(b);World w=Bukkit.getWorld(worldId);if(w==null)return false;
        int size=Coord.getCellSize();if(size<1)throw new IllegalStateException("Неверный размер участка Towny");
        int minX=Math.floorDiv(integer(b,"minX"),size),maxX=Math.floorDiv(integer(b,"maxX"),size),minZ=Math.floorDiv(integer(b,"minZ"),size),maxZ=Math.floorDiv(integer(b,"maxZ"),size);
        long width=(long)maxX-minX+1,depth=(long)maxZ-minZ+1;if(width<1||depth<1||width>4096||depth>4096||width*depth>4096)return false;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){var owner=TownyAPI.getInstance().getTown(new Location(w,(double)x*size,w.getMinHeight(),(double)z*size));if(owner==null||!town.equals(owner.getUUID()))return false;}
        return true; // Towny's territory metadata does not load Minecraft chunks.
    }
}
