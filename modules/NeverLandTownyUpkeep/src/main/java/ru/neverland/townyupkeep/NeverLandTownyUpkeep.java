package ru.neverland.townyupkeep;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townyupkeep.api.TownyUpkeepApi;
import ru.neverland.townyupkeep.config.UpkeepSettings;
import ru.neverland.townyupkeep.data.UpkeepRepository;
import ru.neverland.townyupkeep.service.UpkeepService;
import ru.neverland.townyupkeep.command.UpkeepCommand;
import ru.neverland.townyupkeep.gui.UpkeepMenu;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class NeverLandTownyUpkeep extends JavaPlugin {
    private UpkeepService service;private boolean townCommand;
    @Override public void onEnable(){try{
        saveDefaultConfig();if(!new File(getDataFolder(),"buildings.yml").exists())saveResource("buildings.yml",false);
        var repo=new UpkeepRepository(getDataFolder().toPath().resolve("upkeep-data.yml"));repo.load();service=new UpkeepService(this,repo,settings());
        var menu=new UpkeepMenu(this,service);var command=new UpkeepCommand(this,service,menu);var direct=getCommand("townyupkeep");if(direct==null)throw new IllegalStateException("Нет команды townyupkeep");direct.setExecutor(command);direct.setTabCompleter(command);
        townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"upkeep",command);if(!townCommand)getLogger().warning("/t upkeep занят; используйте /townyupkeep");
        getServer().getPluginManager().registerEvents(menu,this);service.start();getServer().getServicesManager().register(TownyUpkeepApi.class,service,this,ServicePriority.Normal);
        getLogger().info("Обслуживание включено: "+service.settings().profiles().size()+" профилей; период "+service.settings().period()+" сек.");
    }catch(Exception|LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Обслуживание не загружено. Данные не перезаписаны; здания приостановлены до исправления причины.",ex);getServer().getPluginManager().disablePlugin(this);}}
    private YamlConfiguration read(String name)throws Exception{var y=new YamlConfiguration();y.load(new File(getDataFolder(),name));try(var input=getResource(name)){if(input!=null)y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(input,StandardCharsets.UTF_8)));}y.options().copyDefaults(true);return y;}
    private UpkeepSettings settings()throws Exception{return UpkeepSettings.load(read("config.yml"),read("buildings.yml"));}
    public boolean reloadUpkeep(){try{service.reload(settings());return true;}catch(Exception ex){getLogger().warning("Настройки обслуживания не применены: "+ex.getMessage());return false;}}
    @Override public void onDisable(){if(service!=null)service.stop();getServer().getServicesManager().unregisterAll(this);if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"upkeep");}
}
