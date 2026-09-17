package ru.neverland.townycrime;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import ru.neverland.townycrime.api.TownyCrimeApi;
public final class NeverLandTownyCrime extends JavaPlugin {
    private CrimeService service;private boolean townCommand;private CrimeExpansion expansion;
    private CrimeSettings settings()throws Exception{var config=new YamlConfiguration();config.load(new File(getDataFolder(),"config.yml"));try(var stream=getResource("config.yml")){config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8)));}config.options().copyDefaults(true);return CrimeSettings.load(config);}
    public void reloadCrime()throws Exception{service.reload(settings());reloadConfig();}
    @Override public void onEnable(){
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;
try{saveDefaultConfig();var repository=new CrimeRepository(getDataFolder().toPath().resolve("crime-data.yml"));repository.load();service=new CrimeService(this,repository,settings());var menu=new CrimeMenu(this,service);var command=new CrimeCommand(this,menu);getCommand("townycrime").setExecutor(command);getCommand("townycrime").setTabCompleter(command);getServer().getPluginManager().registerEvents(menu,this);
        townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"crime",command);if(!townCommand)getLogger().warning("/t crime занят; используйте /townycrime");service.start();getServer().getServicesManager().register(TownyCrimeApi.class,service,this,ServicePriority.Normal);
        if(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")){expansion=new CrimeExpansion(service,getPluginMeta().getVersion());expansion.register();}
    }catch(Exception|LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Преступность не загружена; данные не перезаписаны",ex);getServer().getPluginManager().disablePlugin(this);}}
    @Override public void onDisable(){
        if (!ru.neverland.core.ModuleLifecycle.end(this)) return;
if(expansion!=null)expansion.unregister();if(service!=null)service.stop();getServer().getServicesManager().unregisterAll(this);if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"crime");}
}
