package ru.neverland.townydistricts.service;

import java.util.*;
import java.util.concurrent.CompletionStage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.townydistricts.config.AssessmentSettings;
import ru.neverland.townydistricts.model.*;
import static ru.neverland.townydistricts.model.DistrictAssessment.*;

/** Captures only Towny metadata and already published building footprints on the caller; the graph runs detached. */
public final class AssessmentService {
    private final JavaPlugin plugin;private final DistrictService districts;private final DistrictPlanner planner;private Settings settings;private String issue="";
    public AssessmentService(JavaPlugin plugin,DistrictService districts){this.plugin=plugin;this.districts=districts;planner=new DistrictPlanner(DistrictAssessment::calculate,error->plugin.getLogger().warning("Оценка районов недоступна для снимка: "+error.getMessage()));reload();}
    public void reload(){try{Object enabled=plugin.getConfig().get("assessment.enabled",true);if(!(enabled instanceof Boolean))throw new IllegalArgumentException("assessment.enabled: требуется true или false");settings=AssessmentSettings.load(plugin.getConfig());for(var group:settings.needs().values())for(var need:group)for(String project:need.projects())if(!districts.settings().projects().containsKey(project))throw new IllegalArgumentException("Неизвестное здание службы: "+project);issue="";}catch(RuntimeException error){settings=null;issue="Настройки оценки требуют проверки администратором";plugin.getLogger().warning("Оценка районов приостановлена; прежние районы и бонусы сохранены: "+error.getMessage());}}
    private static Map<String,Object> unavailable(String reason,boolean pending){return Map.of("available",false,"pending",pending,"issue",reason,"rows",Map.of());}
    public Map<String,Object> assess(UUID id){
        if(!plugin.isEnabled()||ru.neverland.core.SuiteState.frozen(plugin.getName())||!plugin.getConfig().getBoolean("assessment.enabled",true))return unavailable("Оценка районов сейчас отключена",false);
        if(settings==null)return unavailable(issue,false);var town=TownyAPI.getInstance().getTown(id);if(town==null)return unavailable("Город не найден",false);if(town.getTownBlocks().size()>MAX_CLAIMS)return unavailable("Оценка для такого большого города пока недоступна",false);
        var source=districts.assessmentSource(id);if(source==null){if(districts.list(id).isEmpty())source=new DistrictService.View(List.of(),List.of(),new DistrictRules.Evaluation(Map.of(),Map.of(),Set.of()),true);else return unavailable("Данные районов пока недоступны. Обновите окно через несколько секунд",true);}
        var worlds=new HashSet<UUID>();Bukkit.getWorlds().forEach(w->worlds.add(w.getUID()));Cell home=null;try{var block=town.getHomeBlock();var world=Bukkit.getWorld(block.getWorldCoord().getWorldName());if(world!=null)home=new Cell(world.getUID(),block.getX(),block.getZ());}catch(com.palmergames.bukkit.towny.exceptions.TownyException ignored){}
        final Input input;try{input=new Input(source.districts(),source.buildings(),districts.settings().types(),districts.owned(town),worlds,home,districts.cellSize(),!source.paused(),settings);}catch(IllegalArgumentException error){return unavailable("Объём данных слишком велик для текущей оценки",false);}
        var reading=planner.read(id,input);if(!reading.status().equals("READY"))return unavailable(reading.issue(),reading.status().equals("PENDING"));
        var rows=new LinkedHashMap<String,Object>();var definitions=new HashMap<String,District>();input.districts().forEach(d->definitions.put(d.id(),d));
        for(var row:reading.result().rows().values()){
            var d=definitions.get(row.id());var fields=new LinkedHashMap<String,Object>();fields.put("id",row.id());fields.put("name",d.name());fields.put("type",d.type().name());fields.put("world",d.cells().iterator().next().world().toString());fields.put("worldAvailable",row.worldAvailable());fields.put("total",row.total());fields.put("owned",row.owned());fields.put("connected",row.connected());fields.put("distance",row.distance());fields.put("matching",row.matching());fields.put("other",row.other());fields.put("unfinished",row.unfinished());fields.put("crossing",row.crossing());fields.put("unknownBuildings",row.unknownBuildings());fields.put("buildingsAvailable",row.buildingsAvailable());
            var services=new LinkedHashMap<String,Object>();for(var coverage:row.services())services.put(coverage.id(),Map.of("title",coverage.title(),"served",coverage.served(),"total",coverage.total(),"known",coverage.known(),"sources",coverage.sources().stream().sorted().toList()));fields.put("services",Map.copyOf(services));rows.put(row.id(),Map.copyOf(fields));
        }
        return Map.of("available",true,"pending",false,"issue","","checkedAt",reading.at(),"sourceAt",districts.assessmentSourceTime(),"homeKnown",reading.result().homeKnown(),"claimed",reading.result().claimed(),"reached",reading.result().reached(),"range",reading.result().range(),"rows",Map.copyOf(rows));
    }
    public CompletionStage<Void> prepareClose(){return planner.prepareClose();}public void awaitClosed(){planner.awaitClosed();}
}
