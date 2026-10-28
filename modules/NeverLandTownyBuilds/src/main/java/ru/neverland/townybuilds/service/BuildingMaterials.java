package ru.neverland.townybuilds.service;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.bukkit.*;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.townybuilds.api.BuildingMaterialsApi;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.storage.StockMath;
import ru.neverland.townybuilds.util.ItemCodec;
public final class BuildingMaterials implements BuildingMaterialsApi {
    private final JavaPlugin plugin;private final DataStore data;private final DefinitionRegistry definitions;private final RussianItemNames names;
    public BuildingMaterials(JavaPlugin plugin,DataStore data,DefinitionRegistry definitions,RussianItemNames names){this.plugin=plugin;this.data=data;this.definitions=definitions;this.names=names;Bukkit.getServicesManager().register(BuildingMaterialsApi.class,this,plugin,ServicePriority.Normal);}
    @Override public Collection<Map<String,Object>> plans(UUID town){try(var guard=SuiteState.guard()){
        if(!plugin.isEnabled()||SuiteState.frozen(plugin.getName())||!data.writable()||com.palmergames.bukkit.towny.TownyAPI.getInstance().getTown(town)==null)throw new IllegalArgumentException("Планы города временно недоступны");
        var state=data.town(town);var out=new TreeMap<String,Map<String,Object>>();
        for(var e:state.placements().entrySet()){
            var project=definitions.project(e.getKey());if(project==null||e.getValue().level()!=state.level(e.getKey())+1||project.level(e.getValue().level())==null)throw new IllegalStateException("Сохранённый план требует проверки: "+e.getKey());
            var site=state.constructionSite(e.getKey());if(site!=null&&site.active())continue;var receipt=state.constructionReceipts().get(e.getKey());
            boolean paying=receipt!=null&&Set.of("DEBIT_PENDING","DEBIT_CONFIRMED","ARTIFACT_PENDING").contains(receipt.state());
            long cents=paying?0:ru.neverland.townybuilds.construction.ConstructionReceipt.cents(project.level(e.getValue().level()).money());
            out.put(e.getKey(),Map.of("project",e.getKey(),"title",ru.neverland.townybuilds.util.ColorUtil.plain(project.name()),"level",e.getValue().level(),"cents",cents,"known",!paying,"state",paying?"REVIEW":"PLANNED"));
        }
        for(var e:state.constructionReceipts().entrySet())if(Set.of("DEBIT_PENDING","DEBIT_CONFIRMED","ARTIFACT_PENDING").contains(e.getValue().state()))out.put(e.getKey(),Map.of("project",e.getKey(),"title",e.getKey(),"level",e.getValue().level(),"cents",0L,"known",false,"state","REVIEW"));
        return List.copyOf(out.values());
    }}
    public Map<String,Object> needs(UUID town,String project){try(var guard=SuiteState.guard()){
        if(!plugin.isEnabled()||SuiteState.frozen(plugin.getName())||!data.writable()||com.palmergames.bukkit.towny.TownyAPI.getInstance().getTown(town)==null)throw new IllegalArgumentException("Материалы города временно недоступны");
        var def=definitions.project(project);if(def==null)throw new IllegalArgumentException("Здание не найдено");var state=data.town(town);int next=state.level(project)+1;
        if(next>def.maxLevel())throw new IllegalArgumentException("Здание уже достигло максимального уровня");var site=state.constructionSite(project);if(site!=null&&site.active())throw new IllegalArgumentException("Здание уже строится");
        if(data.storageBusy(town,"warehouse"))throw new IllegalArgumentException("Склад сейчас открыт. Закройте его перед расчётом заказа");
        var fund=state.existingResourceFund(project);var result=new ArrayList<Map<String,Object>>();
        for(var required:def.level(next).resources()){
            int donated=fund!=null&&fund.targetLevel()==next?fund.count(required):0;long stored=StockMath.count(state.storage(),required);long missing=Math.max(0,(long)required.getAmount()-donated-stored);if(missing==0)continue;
            var sample=required.clone();sample.setAmount(1);result.add(Map.of("item",Base64.getEncoder().encodeToString(sample.serializeAsBytes()),"amount",Math.toIntExact(missing),"material",sample.getType().name(),"name",names.name(sample)));
        }
        String title=ru.neverland.townybuilds.util.ColorUtil.plain(def.name());String fingerprint=UUID.nameUUIDFromBytes((town+":"+project+":"+next+":"+result.stream().map(m->m.get("item")+":"+m.get("amount")).sorted().toList()).getBytes(StandardCharsets.UTF_8)).toString();
        return Map.of("town",town,"project",project,"title",title,"targetLevel",next,"fingerprint",fingerprint,"materials",List.copyOf(result));
    }}
}
