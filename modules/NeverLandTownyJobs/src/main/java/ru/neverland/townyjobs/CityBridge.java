package ru.neverland.townyjobs;
import java.util.*;import org.bukkit.*;import com.palmergames.bukkit.towny.object.*;
public final class CityBridge {
    private Object api;private Class<?> type;
    private ru.neverland.core.ApiServices.Connection connection;
    public void verify()throws Exception{connection=ru.neverland.core.ApiServices.connect("NeverLandTownyBuilds","ru.neverland.townybuilds.api.TownyBuildsApi",1,"supportedProjects","workplaces");if(!connection.ready())throw new IllegalStateException("Постройки: "+connection.state()+" "+connection.detail());type=connection.contract();api=connection.service();}
    public Set<String> known()throws Exception{verify();Object value=connection.invoke("supportedProjects",new Class<?>[]{});if(!(value instanceof Set<?> set)||set.isEmpty()||set.stream().anyMatch(v->!(v instanceof String)))throw new IllegalStateException("Неверный каталог зданий API");return Set.copyOf((Set<String>)set);}
    private static int n(Object b,String key)throws Exception{return ((Number)b.getClass().getMethod(key).invoke(b)).intValue();}
    public Map<String,WorkPolicy.Site> sites(UUID town)throws Exception{
        verify();Map<String,WorkPolicy.Site> result=new HashMap<>();var values=(Map<?,?>)connection.invoke("workplaces",new Class<?>[]{UUID.class},town);
        for(var entry:values.entrySet()){String id=entry.getKey().toString();Object b=entry.getValue();UUID world=(UUID)b.getClass().getMethod("worldId").invoke(b);World w=Bukkit.getWorld(world);int level=Math.max(0,Math.min(5,n(b,"completedLevel"))),minX=n(b,"minX"),maxX=n(b,"maxX"),minZ=n(b,"minZ"),maxZ=n(b,"maxZ");boolean owned=w!=null;int size=Coord.getCellSize();if(size<1||(long)maxX-minX>1024||(long)maxZ-minZ>1024)throw new IllegalStateException("Неверный участок здания");
            for(int x=Math.floorDiv(minX,size);owned&&x<=Math.floorDiv(maxX,size);x++)for(int z=Math.floorDiv(minZ,size);z<=Math.floorDiv(maxZ,size);z++){var t=WorldCoord.parseWorldCoord(new Location(w,(double)x*size,w.getMinHeight(),(double)z*size)).getTownOrNull();if(t==null||!town.equals(t.getUUID())){owned=false;break;}}
            result.put(id,new WorkPolicy.Site(world,minX,minZ,maxX,maxZ,n(b,"y"),level,owned,ru.neverland.integration.BuildingOperations.active(town,id)));
        }return Map.copyOf(result);
    }
}
