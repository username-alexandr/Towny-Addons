package ru.neverland.townysieges;

import java.util.*;
import ru.neverland.core.ApiServices;
import static ru.neverland.townysieges.SiegeRules.*;

/** Reads only the public Builds workplace contract. Live operational level is rechecked for every restriction. */
public final class BuildsAccess {
    public record Building(int completed, Zone zone) { }
    public Map<Fort,Building> buildings(UUID town,SiegeSettings settings) throws Exception {
        var api=ApiServices.require("NeverLandTownyBuilds","ru.neverland.townybuilds.api.TownyBuildsApi","workplaces","line");
        var workplaces=(Map<?,?>)api.invoke("workplaces",new Class<?>[]{UUID.class},town);
        var out=new EnumMap<Fort,Building>(Fort.class);
        for (Fort fort:Fort.values()) {
            Object place=workplaces.get(fort.project); if(place==null) continue;
            var type=place.getClass();
            UUID world=(UUID)type.getMethod("worldId").invoke(place);
            double x1=num(place,"minX"),x2=num(place,"maxX")+1,z1=num(place,"minZ"),z2=num(place,"maxZ")+1,y=num(place,"y");
            double radius=settings.radii().get(fort),height=fort==Fort.TOWER||fort==Fort.KEEP?settings.towerHeight():settings.groundHeight();
            Zone zone=new Zone(world,x1-radius,y-8,z1-radius,x2+radius,y+height,z2+radius);
            if(fort==Fort.WALL||fort==Fort.MOAT){
                Object line=((Optional<?>)api.invoke("line",new Class<?>[]{UUID.class,String.class},town,fort.project)).orElse(null);
                if(line!=null){UUID lineWorld=(UUID)line.getClass().getMethod("worldId").invoke(line);zone=Zone.along(lineWorld,num(line,"x1")+.5,num(line,"y1"),num(line,"z1")+.5,num(line,"x2")+.5,num(line,"y2"),num(line,"z2")+.5,radius+1,height);}
            }
            out.put(fort,new Building((int)num(place,"completedLevel"),zone));
        }
        return Map.copyOf(out);
    }
    static double num(Object record,String name)throws Exception { return ((Number)record.getClass().getMethod(name).invoke(record)).doubleValue(); }
    public int level(UUID town,Fort fort,Building building)throws Exception {
        if(building==null) return 0;
        int live=((Number)ApiServices.call("NeverLandTownyBuilds","ru.neverland.townybuilds.api.TownyBuildsApi","operationalLevel",new Class<?>[]{UUID.class,String.class},town,fort.project)).intValue();
        return Math.max(0,Math.min(5,Math.min(live,building.completed())));
    }
    public boolean protectedByTreaty(UUID attacker,UUID defender)throws Exception {
        if(attacker==null) return false;
        var api=ApiServices.connect("NeverLandTownyDiplomacy","ru.neverland.townydiplomacy.api.TownyDiplomacyApi",1,"healthy","hostileBlocked");
        if(api.state()==ApiServices.State.NOT_INSTALLED) return false;
        if(!api.ready() || !Boolean.TRUE.equals(api.invoke("healthy",new Class<?>[]{}))) throw new IllegalStateException("Дипломатия недоступна");
        return Boolean.TRUE.equals(api.invoke("hostileBlocked",new Class<?>[]{UUID.class,UUID.class},attacker,defender));
    }
    public double army(UUID town)throws Exception {
        var api=ApiServices.connect("NeverLandTownyArmy","ru.neverland.townyarmy.api.TownyArmyApi",1,"healthy","garrison");
        if(api.state()==ApiServices.State.NOT_INSTALLED) return 0;
        if(!api.ready() || !Boolean.TRUE.equals(api.invoke("healthy",new Class<?>[]{}))) throw new IllegalStateException("Гарнизон недоступен");
        Object value=((Optional<?>)api.invoke("garrison",new Class<?>[]{UUID.class},town)).orElse(null);
        if(value==null) return 0;
        Object bonus=((Map<?,?>)value).get("defenseBonus");
        if(!(bonus instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue()<0) throw new IllegalStateException("Неверный бонус гарнизона");
        return Math.min(.2,n.doubleValue());
    }
}
