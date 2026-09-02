package ru.neverland.mintespionage.integration;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class BuildBridge {
    public record Defense(double successPenalty,double detectionBonus,Map<String,Integer> levels){}
    private final JavaPlugin plugin;private boolean warned;
    public BuildBridge(JavaPlugin plugin){this.plugin=plugin;}
    public Defense defense(UUID townId){
        ConfigurationSection section=plugin.getConfig().getConfigurationSection("buildings.defense");if(section==null)return new Defense(0,0,Map.of());
        Map<String,Integer> levels=new LinkedHashMap<>();double penalty=0,detection=0;
        for(String project:section.getKeys(false)){
            int level=level(townId,project);levels.put(project,level);penalty+=level*section.getDouble(project+".success-penalty",0);detection+=level*section.getDouble(project+".detection-bonus",0);
        }
        return new Defense(penalty,detection,Map.copyOf(levels));
    }
    public Map<String,Integer> knownLevels(UUID townId){
        String[] projects={"town_hall","forge","barracks","market","miners_guild","temple","great_library","agrarian_complex"};
        Map<String,Integer> result=new LinkedHashMap<>();for(String project:projects)result.put(project,level(townId,project));return result;
    }
    private int level(UUID townId,String project){
        Plugin source=Bukkit.getPluginManager().getPlugin(plugin.getConfig().getString("buildings.plugin","NeverLandTownyBuilds"));
        if(source==null||!source.isEnabled())return 0;
        try{
            Field field=source.getClass().getDeclaredField("dataStore");field.setAccessible(true);Object store=field.get(source);
            Object townData=store.getClass().getMethod("town",UUID.class).invoke(store,townId);Method method=townData.getClass().getMethod("level",String.class);
            return Math.max(0,((Number)method.invoke(townData,project)).intValue());
        }catch(ReflectiveOperationException|RuntimeException exception){if(!warned){warned=true;plugin.getLogger().warning("Не удалось прочитать уровни оборонных зданий: "+exception.getMessage());}return 0;}
    }
}
