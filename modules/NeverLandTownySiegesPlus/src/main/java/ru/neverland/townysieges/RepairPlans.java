package ru.neverland.townysieges;
import java.util.*;
import org.bukkit.entity.Player;
import ru.neverland.core.*;
public final class RepairPlans {
    public record Cell(RaidStore.Key key,String material,String data,String top,String hpMaterial){public Cell(RaidStore.Key key,String material,String data,String top){this(key,material,data,top,material);}}
    public record Plan(UUID town,UUID world,String project,String title,int level,long price,boolean active,String fingerprint,List<Cell> cells){public Plan{cells=List.copyOf(cells);}}
    private record Cached(long at,Map<String,Target> projects){}
    public record Target(String project,String hpMaterial){}
    private final Map<UUID,Cached> cached=new HashMap<>();
    private ApiServices.Connection api()throws Exception{return ApiServices.require("NeverLandTownyBuilds","ru.neverland.townybuilds.api.BuildingRepairPlansApi","plans","plan","canRepair");}
    @SuppressWarnings("unchecked") public Plan plan(UUID town,String project)throws Exception {var map=(Map<String,Object>)api().invoke("plan",new Class<?>[]{UUID.class,String.class},town,project);if(map.isEmpty())throw new IllegalArgumentException("У здания нет завершённой зарегистрированной постройки");return parse(town,map);}
    @SuppressWarnings("unchecked") private Plan parse(UUID town,Map<String,Object> map){UUID world=(UUID)map.get("world");var cells=new ArrayList<Cell>();for(var c:(List<Map<String,Object>>)map.get("cells"))if(!TntRules.protectedMaterial((String)c.get("material")))cells.add(new Cell(new RaidStore.Key(world,((Number)c.get("x")).intValue(),((Number)c.get("y")).intValue(),((Number)c.get("z")).intValue()),(String)c.get("material"),(String)c.get("data"),(String)c.get("top"),(String)c.getOrDefault("baseMaterial",c.get("material"))));return new Plan(town,world,(String)map.get("project"),(String)map.get("title"),((Number)map.get("level")).intValue(),((Number)map.get("price")).longValue(),Boolean.TRUE.equals(map.get("active")),(String)map.get("fingerprint"),cells);}
    public boolean canRepair(Player player,UUID town)throws Exception{return Boolean.TRUE.equals(api().invoke("canRepair",new Class<?>[]{Player.class,UUID.class},player,town));}
    public String project(UUID town,RaidStore.Key key,String material)throws Exception{return target(town,key,material).project();}
    @SuppressWarnings("unchecked") public Target target(UUID town,RaidStore.Key key,String material)throws Exception{
        var prior=cached.get(town);if(prior==null||System.currentTimeMillis()-prior.at()>3000){var out=new HashMap<String,Target>();
            var api=ApiServices.require("NeverLandTownyBuilds","ru.neverland.townybuilds.api.BuildingRepairPlansApi","warPlans");
            for(var map:(Collection<Map<String,Object>>)api.invoke("warPlans",new Class<?>[]{UUID.class},town)){var p=parse(town,map);for(var c:p.cells())out.put(c.key().id()+":"+c.material(),new Target(p.project(),c.hpMaterial()));}
            prior=new Cached(System.currentTimeMillis(),Map.copyOf(out));cached.put(town,prior);
        }return prior.projects().getOrDefault(key.id()+":"+material,new Target("",material));
    }
}
