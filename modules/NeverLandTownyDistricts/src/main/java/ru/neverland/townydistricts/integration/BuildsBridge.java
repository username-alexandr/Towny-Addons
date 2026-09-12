package ru.neverland.townydistricts.integration;
import org.bukkit.Bukkit;
import ru.neverland.townydistricts.model.DistrictRules.Building;
import java.util.*;
public final class BuildsBridge {
    public static void verify()throws ReflectiveOperationException{
        ru.neverland.core.ApiServices.require("NeverLandTownyBuilds","ru.neverland.townybuilds.api.TownyBuildsApi","buildingFootprints");
    }
    public List<Building> buildings(UUID town)throws ReflectiveOperationException{
        Map<?,?> values=(Map<?,?>)ru.neverland.core.ApiServices.call("NeverLandTownyBuilds","ru.neverland.townybuilds.api.TownyBuildsApi","buildingFootprints",new Class<?>[]{UUID.class},town);
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
