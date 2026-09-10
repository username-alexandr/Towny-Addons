package ru.neverland.townydistricts.integration;
import org.bukkit.Bukkit;
import ru.neverland.townydistricts.model.DistrictRules.Building;
import java.util.*;
public final class BuildsBridge {
    public static void verify()throws ReflectiveOperationException{
        var plugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds");
        if(plugin==null)throw new IllegalStateException("Требуется NeverLandTownyBuilds 0.7.8 или новее");
        Class.forName("ru.neverland.townybuilds.api.TownyBuildsApi",true,plugin.getClass().getClassLoader()).getMethod("buildingFootprints",UUID.class);
    }
    public List<Building> buildings(UUID town)throws ReflectiveOperationException{
        var plugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds");
        if(plugin==null||!plugin.isEnabled())throw new IllegalStateException("Постройки недоступны");
        Class<?> api=Class.forName("ru.neverland.townybuilds.api.TownyBuildsApi",true,plugin.getClass().getClassLoader());
        Object provider=Bukkit.getServicesManager().load(api);if(provider==null)throw new IllegalStateException("API построек недоступен");
        Map<?,?> values=(Map<?,?>)api.getMethod("buildingFootprints",UUID.class).invoke(provider,town);
        List<Building> result=new ArrayList<>();
        for(var entry:values.entrySet()){
            Object b=entry.getValue();Class<?> type=b.getClass();
            result.add(new Building(entry.getKey().toString(),(UUID)type.getMethod("worldId").invoke(b),
                    integer(type,b,"minX"),integer(type,b,"minZ"),integer(type,b,"maxX"),integer(type,b,"maxZ"),
                    ru.neverland.integration.BuildingOperations.level(town,entry.getKey().toString(),integer(type,b,"completedLevel")),integer(type,b,"requiredLevel")));
        }
        return List.copyOf(result);
    }
    private int integer(Class<?> type,Object value,String name)throws ReflectiveOperationException{return ((Number)type.getMethod(name).invoke(value)).intValue();}
}
