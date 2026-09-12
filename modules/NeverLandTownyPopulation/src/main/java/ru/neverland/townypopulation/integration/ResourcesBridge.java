package ru.neverland.townypopulation.integration;
import org.bukkit.Bukkit;
import ru.neverland.townypopulation.model.StrategicSupply;
import java.util.*;
public final class ResourcesBridge {
    private ResourcesBridge() {}
    public static Optional<StrategicSupply> supply(UUID town)throws ReflectiveOperationException {
        var api=ru.neverland.core.ApiServices.connect("NeverLandTownyResources","ru.neverland.townyresources.api.TownyResourcesApi",1,"resources");
        if(api.state()==ru.neverland.core.ApiServices.State.NOT_INSTALLED)return Optional.empty();
        if(!api.ready())return Optional.of(new StrategicSupply(1,1,true));
        var value=(Optional<?>)api.invoke("resources",new Class<?>[]{UUID.class},town);
        if(value.isEmpty())return Optional.of(new StrategicSupply(1,1,true));Object snapshot=value.get();Class<?> c=snapshot.getClass();
        if(!(Boolean)c.getMethod("populationLinked").invoke(snapshot))return Optional.empty();
        return Optional.of(new StrategicSupply(((Number)c.getMethod("foodCoverage").invoke(snapshot)).doubleValue(),((Number)c.getMethod("waterCoverage").invoke(snapshot)).doubleValue(),(Boolean)c.getMethod("paused").invoke(snapshot)));
    }
}
