package ru.neverland.townypopulation.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import java.util.*;

/** Uses Builds' public service, never its private datastore or construction files. */
public final class BuildsBridge {
    public Map<String,Integer> levels(UUID town, Set<String> projects) throws ReflectiveOperationException {
        var api=ru.neverland.core.ApiServices.require("NeverLandTownyBuilds","ru.neverland.townybuilds.api.TownyBuildsApi","operationalLevel");
        Map<String,Integer> levels = new HashMap<>();
        for (String id : projects) levels.put(id,Math.max(0,((Number)api.invoke("operationalLevel",new Class<?>[]{UUID.class,String.class},town,id)).intValue()));
        return levels;
    }
}
