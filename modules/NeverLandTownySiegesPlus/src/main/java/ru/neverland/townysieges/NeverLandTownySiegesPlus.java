package ru.neverland.townysieges;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import ru.neverland.townysieges.api.TownySiegesApi;

public final class NeverLandTownySiegesPlus extends JavaPlugin {
    private SiegeService service;private BukkitTask task;private boolean townCommand;
    private SiegeSettings settings()throws Exception{var yaml=new YamlConfiguration();yaml.load(new File(getDataFolder(),"config.yml"));try(var stream=getResource("config.yml")){yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8)));}return SiegeSettings.load(yaml);}
    public void reloadSieges()throws Exception{var next=settings();service.reload(next);reloadConfig();}
    @Override public void onEnable(){try{saveDefaultConfig();service=new SiegeService(this,settings());var menu=new SiegeMenu(this,service);var command=new SiegeCommand(this,service,menu);getCommand("townysieges").setExecutor(command);getCommand("townysieges").setTabCompleter(command);getServer().getPluginManager().registerEvents(menu,this);getServer().getPluginManager().registerEvents(new SiegeListener(service),this);townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"sieges",command);if(!townCommand)getLogger().warning("/t sieges занят; используйте /sieges");getServer().getServicesManager().register(TownySiegesApi.class,service,this,ServicePriority.Normal);task=getServer().getScheduler().runTaskTimer(this,service::pulse,20,20);service.war().connect();getLogger().info("Sieges+: "+service.war().detail());}catch(Exception|LinkageError e){getLogger().log(java.util.logging.Level.SEVERE,"Sieges+ не загружен: проверьте конфигурацию",e);getServer().getPluginManager().disablePlugin(this);}}
    @Override public void onDisable(){if(task!=null)task.cancel();if(service!=null)service.stop();getServer().getServicesManager().unregisterAll(this);if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"sieges");}
}
