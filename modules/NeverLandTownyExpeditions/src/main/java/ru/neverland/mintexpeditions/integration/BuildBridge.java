package ru.neverland.mintexpeditions.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/** Необязательный мост без жёсткой зависимости и без цикла загрузки Paper. */
public final class BuildBridge {
    private final JavaPlugin plugin;


    public BuildBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public double expeditionTimeMultiplier(UUID townId) {
        if (projectLevel(townId, "celestial_orrery") <= 0) return 1.0;
        return Math.max(1.0, plugin.getConfig().getDouble("builds.celestial-orrery-time-multiplier", 1.25));
    }

    private int projectLevel(UUID townId,String project) {
        if(townId==null||project==null||project.isBlank())return 0;
        var api=ru.neverland.core.ApiServices.connect(plugin.getConfig().getString("builds.plugin","NeverLandTownyBuilds"),"ru.neverland.townybuilds.api.TownyBuildsApi",1,"operationalLevel");
        if(!api.ready())return 0;
        try{return Math.max(0,((Number)api.invoke("operationalLevel",new Class<?>[]{UUID.class,String.class},townId,project)).intValue());}
        catch(ReflectiveOperationException|RuntimeException ex){return 0;}
    }
}
