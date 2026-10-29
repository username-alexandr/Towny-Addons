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
    public Defense defense(UUID townId){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        double army=ArmyAccess.defense(townId);
        ConfigurationSection section=plugin.getConfig().getConfigurationSection("buildings.defense");if(section==null)return new Defense(army,army,Map.of());
        Map<String,Integer> levels=new LinkedHashMap<>();double penalty=army,detection=army;
        for(String project:section.getKeys(false)){
            int level=level(townId,project);levels.put(project,level);penalty+=level*section.getDouble(project+".success-penalty",0);detection+=level*section.getDouble(project+".detection-bonus",0);
        }
        return new Defense(penalty,detection,Map.copyOf(levels));
    } }
    public Map<String,Integer> knownLevels(UUID townId){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        String[] projects={"town_hall","forge","barracks","market","miners_guild","temple","great_library","agrarian_complex"};
        Map<String,Integer> result=new LinkedHashMap<>();for(String project:projects)result.put(project,level(townId,project));return result;
    } }
    private int level(UUID townId,String project) {
        if(townId==null||project==null||project.isBlank())return 0;
        var api=ru.neverland.core.ApiServices.connect(plugin.getConfig().getString("buildings.plugin","NeverLandTownyBuilds"),"ru.neverland.townybuilds.api.TownyBuildsApi",1,"operationalLevel");
        if(api.state()==ru.neverland.core.ApiServices.State.NOT_INSTALLED)return 0;
        if(!api.ready())throw new IllegalStateException("Сведения о защите зданий недоступны");
        try{Object raw=api.invoke("operationalLevel",new Class<?>[]{UUID.class,String.class},townId,project);if(!(raw instanceof Integer level)||level<0||level>100)throw new IllegalStateException("Неверный уровень защиты здания");return level;}
        catch(ReflectiveOperationException|RuntimeException ex){throw new IllegalStateException("Сведения о защите зданий недоступны",ex);}
    }
}
