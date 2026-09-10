package ru.neverland.townypopulation.integration;
import org.bukkit.Bukkit;
import ru.neverland.townypopulation.model.StrategicSupply;
import java.util.*;
public final class ResourcesBridge {
    private ResourcesBridge() {}
    public static Optional<StrategicSupply> supply(UUID town)throws ReflectiveOperationException {
        var plugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyResources");if(plugin==null)return Optional.empty();
        if(!plugin.isEnabled())return Optional.of(new StrategicSupply(1,1,true));
        Class<?> type=Class.forName("ru.neverland.townyresources.api.TownyResourcesApi",true,plugin.getClass().getClassLoader());Object api=Bukkit.getServicesManager().load(type);
        if(api==null)return Optional.of(new StrategicSupply(1,1,true));
        var value=(Optional<?>)type.getMethod("resources",UUID.class).invoke(api,town);
        if(value.isEmpty())return Optional.of(new StrategicSupply(1,1,true));Object snapshot=value.get();Class<?> c=snapshot.getClass();
        if(!(Boolean)c.getMethod("populationLinked").invoke(snapshot))return Optional.empty();
        return Optional.of(new StrategicSupply(((Number)c.getMethod("foodCoverage").invoke(snapshot)).doubleValue(),((Number)c.getMethod("waterCoverage").invoke(snapshot)).doubleValue(),(Boolean)c.getMethod("paused").invoke(snapshot)));
    }
}
