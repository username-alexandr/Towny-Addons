package ru.neverland.townyjustice;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import ru.neverland.townyjustice.api.TownyJusticeApi;
public final class NeverLandTownyJustice extends JavaPlugin {
    private JusticeService service;private boolean townCommand;private JusticeExpansion expansion;private BukkitTask task;
    private JusticeSettings settings()throws Exception{var config=new YamlConfiguration();config.load(new File(getDataFolder(),"config.yml"));try(var stream=getResource("config.yml")){config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8)));}return JusticeSettings.load(config);}
    public void reloadJustice()throws Exception{service.reload(settings());reloadConfig();}
    @Override public void onEnable(){
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;
try{saveDefaultConfig();var repository=new JusticeRepository(getDataFolder().toPath().resolve("justice-data.yml"));repository.load();service=new JusticeService(this,repository,settings());var menu=new JusticeMenu(this,service);var command=new JusticeCommand(this,service,menu);getCommand("townyjustice").setExecutor(command);getCommand("townyjustice").setTabCompleter(command);getServer().getPluginManager().registerEvents(menu,this);
        townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"justice",command);if(!townCommand)getLogger().warning("/t justice занят; используйте /justice");task=getServer().getScheduler().runTaskTimer(this,service::pulse,40L,40L);getServer().getServicesManager().register(TownyJusticeApi.class,service,this,ServicePriority.Normal);
        if(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")){expansion=new JusticeExpansion(service,getPluginMeta().getVersion());expansion.register();}
    }catch(Exception|LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Суд не загружен; данные не перезаписаны",ex);getServer().getPluginManager().disablePlugin(this);}}
    @Override public void onDisable(){
        if (!ru.neverland.core.ModuleLifecycle.end(this)) return;
if(task!=null)task.cancel();if(expansion!=null)expansion.unregister();getServer().getServicesManager().unregisterAll(this);if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"justice");}
}
