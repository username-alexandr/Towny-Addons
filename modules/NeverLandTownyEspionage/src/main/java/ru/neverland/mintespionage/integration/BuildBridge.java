package ru.neverland.mintespionage.integration;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class BuildBridge {
    public record Defense(double successPenalty,double detectionBonus,Map<String,Integer> levels){}
    private final JavaPlugin plugin;
    public BuildBridge(JavaPlugin plugin){this.plugin=plugin;}
    public Defense defense(UUID townId){
        double army=ArmyAccess.defense(townId);
        ConfigurationSection section=plugin.getConfig().getConfigurationSection("buildings.defense");if(section==null)return new Defense(army,army,Map.of());
        Map<String,Integer> levels=new LinkedHashMap<>();double penalty=army,detection=army;
        for(String project:section.getKeys(false)){
            int level=level(townId,project);levels.put(project,level);penalty+=level*section.getDouble(project+".success-penalty",0);detection+=level*section.getDouble(project+".detection-bonus",0);
        }
        return new Defense(penalty,detection,Map.copyOf(levels));
    }
    public Map<String,Integer> knownLevels(UUID townId){
        String[] projects={"town_hall","forge","barracks","market","miners_guild","temple","great_library","agrarian_complex"};
        Map<String,Integer> result=new LinkedHashMap<>();for(String project:projects)result.put(project,level(townId,project));return result;
    }
    private int level(UUID townId,String project) {
        if(townId==null||project==null||project.isBlank())return 0;
        var api=ru.neverland.core.ApiServices.connect(plugin.getConfig().getString("buildings.plugin","NeverLandTownyBuilds"),"ru.neverland.townybuilds.api.TownyBuildsApi",1,"operationalLevel");
        if(!api.ready())return 0;
        try{return Math.max(0,((Number)api.invoke("operationalLevel",new Class<?>[]{UUID.class,String.class},townId,project)).intValue());}
        catch(ReflectiveOperationException|RuntimeException ex){return 0;}
    }
}
