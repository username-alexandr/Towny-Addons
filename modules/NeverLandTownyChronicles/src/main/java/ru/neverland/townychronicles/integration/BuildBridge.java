package ru.neverland.townychronicles.integration;

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
    private final JavaPlugin plugin;private boolean warned;public BuildBridge(JavaPlugin plugin){this.plugin=plugin;}
    public Map<String,String> projects(){ConfigurationSection section=plugin.getConfig().getConfigurationSection("wonders.projects");if(section==null)return Map.of();Map<String,String> result=new LinkedHashMap<>();for(String key:section.getKeys(false))result.put(key,section.getString(key,key));return result;}
    public int level(UUID townId,String project){Plugin source=Bukkit.getPluginManager().getPlugin(plugin.getConfig().getString("wonders.plugin","NeverLandTownyBuilds"));if(source==null||!source.isEnabled())return 0;try{Field field=source.getClass().getDeclaredField("dataStore");field.setAccessible(true);Object store=field.get(source);Object data=store.getClass().getMethod("town",UUID.class).invoke(store,townId);Method level=data.getClass().getMethod("level",String.class);return Math.max(0,((Number)level.invoke(data,project)).intValue());}catch(ReflectiveOperationException|RuntimeException exception){if(!warned){warned=true;plugin.getLogger().warning("Не удалось прочитать Чудеса Света: "+exception.getMessage());}return 0;}}
}
