package cc.crafto.ui.server;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CityTreeCatalogTest {
    @Test void curatedTreeHasReachableBuildingsAndNoCycles(){
        var yaml=YamlConfiguration.loadConfiguration(new File("../addon-overrides/projects.yml"));
        var projects=Objects.requireNonNull(yaml.getConfigurationSection("buildings"));
        assertEquals(90,projects.getKeys(false).size());
        var finished=new HashSet<String>();
        for(var id:projects.getKeys(false))walk(id,projects,new HashSet<>(),finished);
        assertEquals(Set.of("town_hall"),projects.getKeys(false).stream().filter(id->{var s=projects.getConfigurationSection(id);return s==null||s.getConfigurationSection("requires")==null;}).collect(java.util.stream.Collectors.toSet()));
        assertEquals(90,finished.size());
    }
    @Test void completeCityFitsTheNetworkPayload(){
        var projects=Objects.requireNonNull(YamlConfiguration.loadConfiguration(new File("../addon-overrides/projects.yml")).getConfigurationSection("buildings"));
        var built=new HashMap<String,Integer>();var nodes=new ArrayList<CityProgression.Node>();
        for(var id:projects.getKeys(false)){
            built.put(id,5);var section=Objects.requireNonNull(projects.getConfigurationSection(id));var needs=section.getConfigurationSection("requires");var parents=new HashMap<String,Integer>();
            if(needs!=null)for(var parent:needs.getKeys(false))parents.put(parent,needs.getInt(parent));
            nodes.add(new CityProgression.Node(id,id,"building","Город","",5,5,parents,Map.of()));
        }
        var studies=new ArrayList<CityProgression.Node>();
        for(var id:List.of("irrigation","walls","fast_caravans","medicine","navigation","metallurgy","architecture"))
            studies.add(new CityProgression.Node(id,"Изучено: "+id,"research","Исследования","Городская технология",3,3,Map.of("great_library",3),Map.of()));
        var view=new CivicView();new CityProgression.Snapshot(nodes,studies,"").populate(view,built);
        var json=view.json("token",new HashMap<>(),0,12).toString();
        assertTrue(json.getBytes(StandardCharsets.UTF_8).length<cc.crafto.ui.Protocol.MAX_BYTES-cc.crafto.ui.Protocol.HEADER,json.length()+" bytes exceed protocol limit");
    }
    private void walk(String id,ConfigurationSection projects,Set<String> visiting,Set<String> finished){
        if(finished.contains(id))return;
        assertTrue(visiting.add(id),"Cycle at "+id);
        var section=Objects.requireNonNull(projects.getConfigurationSection(id),"Missing building "+id);
        var needs=section.getConfigurationSection("requires");
        if(needs!=null)for(String parent:needs.getKeys(false)){
            var required=needs.getInt(parent);
            assertTrue(required>0&&required<=5,"Bad level for "+id+" -> "+parent);
            walk(parent,projects,visiting,finished);
        }
        visiting.remove(id);finished.add(id);
    }
}
