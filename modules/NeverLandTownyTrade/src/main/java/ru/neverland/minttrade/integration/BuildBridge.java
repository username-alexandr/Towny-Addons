package ru.neverland.minttrade.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class BuildBridge {
    private final JavaPlugin plugin;

    public BuildBridge(JavaPlugin plugin) { this.plugin = plugin; }
    public int marketLevel(UUID townId) {
        return projectLevel(townId, plugin.getConfig().getString("market.project-id", "market"));
    }
    public int projectLevel(UUID townId,String project) {
        if(townId==null||project==null||project.isBlank())return 0;
        var api=ru.neverland.core.ApiServices.connect(plugin.getConfig().getString("warehouse.plugin","NeverLandTownyBuilds"),"ru.neverland.townybuilds.api.TownyBuildsApi",1,"operationalLevel");
        if(!api.ready())return 0;
        try{return Math.max(0,((Number)api.invoke("operationalLevel",new Class<?>[]{UUID.class,String.class},townId,project)).intValue());}
        catch(ReflectiveOperationException|RuntimeException ex){return 0;}
    }
}
