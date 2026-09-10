package ru.neverland.townypopulation.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import java.util.*;

/** Uses Builds' public service, never its private datastore or construction files. */
public final class BuildsBridge {
    public Map<String,Integer> levels(UUID town, Set<String> projects) throws ReflectiveOperationException {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds");
        if (plugin == null || !plugin.isEnabled()) throw new IllegalStateException("NeverLandTownyBuilds отключён");
        Class<?> api = Class.forName("ru.neverland.townybuilds.api.TownyBuildsApi",true,plugin.getClass().getClassLoader());
        Object provider = Bukkit.getServicesManager().load(api);
        if (provider == null) throw new IllegalStateException("API построек не зарегистрирован");
        Method method = api.getMethod("operationalLevel",UUID.class,String.class);
        Map<String,Integer> levels = new HashMap<>();
        for (String id : projects) levels.put(id,Math.max(0,((Number)method.invoke(provider,town,id)).intValue()));
        return levels;
    }
}
