package ru.neverland.townychronicles.integration;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class BuildBridge {
    private final JavaPlugin plugin;public BuildBridge(JavaPlugin plugin){this.plugin=plugin;}
    public Map<String,String> projects(){ConfigurationSection section=plugin.getConfig().getConfigurationSection("wonders.projects");if(section==null)return Map.of();Map<String,String> result=new LinkedHashMap<>();for(String key:section.getKeys(false))result.put(key,section.getString(key,key));return result;}
    public int level(UUID townId,String project) {
        if(townId==null||project==null||project.isBlank())return 0;
        var api=ru.neverland.core.ApiServices.connect(plugin.getConfig().getString("wonders.plugin","NeverLandTownyBuilds"),"ru.neverland.townybuilds.api.TownyBuildsApi",1,"projectLevel");
        if(!api.ready())return 0;
        try{return Math.max(0,((Number)api.invoke("projectLevel",new Class<?>[]{UUID.class,String.class},townId,project)).intValue());}
        catch(ReflectiveOperationException|RuntimeException ex){return 0;}
    }
}
