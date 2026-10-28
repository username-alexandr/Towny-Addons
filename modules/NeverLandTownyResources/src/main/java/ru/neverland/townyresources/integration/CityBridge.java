package ru.neverland.townyresources.integration;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.*;
import org.bukkit.*;
import ru.neverland.townyresources.model.ResourceEngine.Building;
import java.util.*;

public final class CityBridge {
    private final PowerCoverage coverage=new PowerCoverage();
    public void retain(Set<UUID> towns){coverage.retain(towns);}
    public Map<String,Building> buildings(UUID town)throws ReflectiveOperationException {return buildings(town,false,false);}
    public Map<String,Building> buildings(UUID town,boolean timed,boolean advance)throws ReflectiveOperationException { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        Map<?,?> values=(Map<?,?>)ru.neverland.core.ApiServices.call("NeverLandTownyBuilds","ru.neverland.townybuilds.api.TownyBuildsApi","buildingFootprints",new Class<?>[]{UUID.class},town);Map<String,Building> result=new HashMap<>();
        for(var entry:values.entrySet()){
            Object b=entry.getValue();String id=entry.getKey().toString();int completed=integer(b,"completedLevel");
            boolean serviceActive=ru.neverland.integration.BuildingOperations.maintained(town,id)&&ru.neverland.integration.SpecializationAccess.allowed(town,id);
            double fraction=ru.neverland.integration.BuildingOperations.powered(town,id)?1:0;
            if(timed){var power=ru.neverland.core.ApiServices.connect("NeverLandTownyPower","ru.neverland.townypower.api.TownyPowerApi",1,"uptime");if(power.ready())fraction=coverage.fraction(town,id,(Map<?,?>)power.invoke("uptime",new Class<?>[]{UUID.class,String.class},town,id),advance);else if(power.state()!=ru.neverland.core.ApiServices.State.NOT_INSTALLED&&power.state()!=ru.neverland.core.ApiServices.State.INCOMPATIBLE_API)fraction=0;}
            if(!serviceActive)fraction=0;
            result.put(id,new Building(Math.max(0,Math.min(5,completed)),ru.neverland.integration.JobsAccess.multiplier(town,id,ru.neverland.integration.SpecializationAccess.production(town,id,ru.neverland.integration.ResearchBonuses.production(town,id,ru.neverland.integration.DistrictBonuses.multiplier(town,id)))),owned(town,b),ru.neverland.integration.BuildingOperations.active(town,id),ru.neverland.integration.BuildingOperations.inactiveReason(town,id),ru.neverland.integration.PoliciesAccess.production(town,id),ru.neverland.core.SeasonsAccess.production(town,id)*ru.neverland.core.SeasonsAccess.eventProduction(town,id),ru.neverland.core.EnvironmentAccess.agriculture(town,id),fraction));
        }
        return Map.copyOf(result);
    } }
    private static int integer(Object value,String method)throws ReflectiveOperationException{return ((Number)value.getClass().getMethod(method).invoke(value)).intValue();}
    private boolean owned(UUID town,Object b)throws ReflectiveOperationException{
        UUID worldId=(UUID)b.getClass().getMethod("worldId").invoke(b);World w=Bukkit.getWorld(worldId);if(w==null)return false;
        int size=Coord.getCellSize();if(size<1)throw new IllegalStateException("Неверный размер участка Towny");
        int minX=Math.floorDiv(integer(b,"minX"),size),maxX=Math.floorDiv(integer(b,"maxX"),size),minZ=Math.floorDiv(integer(b,"minZ"),size),maxZ=Math.floorDiv(integer(b,"maxZ"),size);
        long width=(long)maxX-minX+1,depth=(long)maxZ-minZ+1;if(width<1||depth<1||width>4096||depth>4096||width*depth>4096)return false;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){var owner=TownyAPI.getInstance().getTown(new Location(w,(double)x*size,w.getMinHeight(),(double)z*size));if(owner==null||!town.equals(owner.getUUID()))return false;}
        return true; // Towny's territory metadata does not load Minecraft chunks.
    }
    public int population(Town town)throws ReflectiveOperationException{ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        var api=ru.neverland.core.ApiServices.connect("NeverLandTownyPopulation","ru.neverland.townypopulation.api.TownyPopulationApi",1,"population");
        if(api.state()==ru.neverland.core.ApiServices.State.NOT_INSTALLED)return town.getResidents().size();
        var value=(Optional<?>)api.invoke("population",new Class<?>[]{UUID.class},town.getUUID());if(value.isEmpty())throw new IllegalStateException("Ожидается перепись населения");
        return integer(value.get(),"population");
    } }
}
