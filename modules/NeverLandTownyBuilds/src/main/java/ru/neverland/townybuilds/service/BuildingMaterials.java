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
