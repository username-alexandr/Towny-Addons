package ru.neverland.townybuilds.construction;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.townybuilds.api.*;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.integration.TownyHook;
import ru.neverland.townybuilds.service.DefinitionRegistry;

public final class BuildingRepairPlans implements BuildingRepairPlansApi {
    private final JavaPlugin plugin;private final DataStore data;private final DefinitionRegistry definitions;
    private final BuildingBlueprintGenerator generator=new BuildingBlueprintGenerator();
    private final Map<Material,Boolean> tiles=new EnumMap<>(Material.class);
    public BuildingRepairPlans(JavaPlugin plugin,DataStore data,DefinitionRegistry definitions){this.plugin=plugin;this.data=data;this.definitions=definitions;Bukkit.getServicesManager().register(BuildingRepairPlansApi.class,this,plugin,ServicePriority.Normal);}
    private void ready(){ApiServices.primaryThread();if(!plugin.isEnabled()||SuiteState.frozen(plugin.getName())||!data.writable())throw new IllegalStateException("Здания временно недоступны");}
    public boolean canRepair(Player player,UUID town){try(var g=SuiteState.guard()){ready();var hook=new TownyHook();var own=hook.town(player);return own!=null&&own.getUUID().equals(town)&&(hook.isMayor(player,own)||CouncilAccess.check(player,town,"build",false));}}
    public Collection<Map<String,Object>> plans(UUID town){try(var g=SuiteState.guard()){ready();var out=new ArrayList<Map<String,Object>>();for(String project:data.town(town).constructionSites().keySet()){var plan=plan(town,project);if(!plan.isEmpty())out.add(plan);}return List.copyOf(out);}}
    public Collection<Map<String,Object>> warPlans(UUID town){try(var g=SuiteState.guard()){ready();var out=new ArrayList<Map<String,Object>>();for(var entry:data.town(town).constructionSites().entrySet()){String project=entry.getKey();var completed=geometry(town,project,false);if(!completed.isEmpty())out.add(completed);if(entry.getValue().active()){var target=geometry(town,project,true);if(!target.isEmpty()&&!target.equals(completed))out.add(target);}}return List.copyOf(out);}}
    public Map<String,Object> plan(UUID town,String project){return geometry(town,project,false);}
    private Map<String,Object> geometry(UUID town,String project,boolean war){try(var g=SuiteState.guard()){
        ready();var state=data.town(town);var site=state.constructionSite(project);var definition=definitions.project(project);
        int level=site==null?0:war&&site.active()?Math.max(site.completedStage(),site.targetStage()):Math.min(state.level(project),site.completedStage());if(definition==null||site==null||level<1||definition.level(level)==null)return Map.of();
        var blueprint=generator.generateForArchitecture(project,level,site.architectureVersion());if(blueprint==null)return Map.of();
        var base=generator.generateForArchitecture(project,level,BuildingStyle.baseArchitecture(site.architectureVersion()));
        var core=project.equals("town_hall")?Bukkit.getServicesManager().load(RaidStorageApi.class).core(town).orElse(null):null;var cells=new ArrayList<Map<String,Object>>();
        for(var e:blueprint.blocks().entrySet()){
            var expected=e.getValue();var material=expected.material();var blockData=material.createBlockData();ConstructionService.orientExpectedState(blockData,expected,site);
            if(!material.isSolid()&&!(blockData instanceof Door)&&material!=Material.LECTERN)continue;
            var at=site.location(null,e.getKey());boolean main=material==Material.LECTERN&&core!=null&&core.worldId().equals(site.worldId())&&core.x()==at.getBlockX()&&core.y()==at.getBlockY()&&core.z()==at.getBlockZ();
            if(tiles.computeIfAbsent(material,m->m.createBlockData().createBlockState() instanceof TileState)&&!main)continue;
            if(blockData instanceof Door door&&door.getHalf()==Bisected.Half.TOP)continue;
            String top="";if(blockData instanceof Door){var upper=blueprint.blocks().get(new BlockOffset(e.getKey().x(),e.getKey().y()+1,e.getKey().z()));if(upper==null||upper.material()!=material)continue;var bd=material.createBlockData();ConstructionService.orientExpectedState(bd,upper,site);top=bd.getAsString();}
            cells.add(Map.of("x",at.getBlockX(),"y",at.getBlockY(),"z",at.getBlockZ(),"material",material.name(),"baseMaterial",base.blocks().get(e.getKey()).material().name(),"data",blockData.getAsString(),"top",top));
        }
        cells.sort(Comparator.comparingInt((Map<String,Object> c)->(int)c.get("y")).thenComparingInt(c->(int)c.get("x")).thenComparingInt(c->(int)c.get("z")));
        long price=BigDecimal.valueOf(definition.level(level).money()).movePointRight(2).setScale(0,RoundingMode.UNNECESSARY).longValueExact();
        String fingerprint=UUID.nameUUIDFromBytes((site.worldId()+":"+project+":"+level+":"+price+":"+site.active()+":"+cells.stream().map(c->c.get("x")+":"+c.get("y")+":"+c.get("z")+":"+c.get("material")+":"+c.get("data")+":"+c.get("top")).toList()).getBytes(StandardCharsets.UTF_8)).toString();
        return Map.of("project",project,"title",ru.neverland.townybuilds.util.ColorUtil.plain(definition.name()),"world",site.worldId(),"level",level,"price",price,"active",site.active(),"fingerprint",fingerprint,"cells",List.copyOf(cells));
    }}
}
