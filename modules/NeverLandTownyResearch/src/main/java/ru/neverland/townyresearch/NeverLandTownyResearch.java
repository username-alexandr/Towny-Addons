package ru.neverland.townyresearch;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townyresearch.api.TownyResearchApi;
import ru.neverland.townyresearch.config.ResearchSettings;
import ru.neverland.townyresearch.data.ResearchRepository;
import ru.neverland.townyresearch.service.ResearchService;
import ru.neverland.townyresearch.command.ResearchCommand;
import ru.neverland.townyresearch.gui.ResearchMenu;
import ru.neverland.townyresearch.integration.ResearchExpansion;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class NeverLandTownyResearch extends JavaPlugin {
    private ResearchService service;private boolean townCommand;private ResearchExpansion expansion;
    @Override public void onEnable(){try{
        saveDefaultConfig();if(!new File(getDataFolder(),"technologies.yml").exists())saveResource("technologies.yml",false);
        var repository=new ResearchRepository(getDataFolder().toPath().resolve("research-data.yml"));repository.load();service=new ResearchService(this,repository,settings());
        var menu=new ResearchMenu(this,service);var command=new ResearchCommand(this,service,menu);var direct=getCommand("townyresearch");if(direct==null)throw new IllegalStateException("Нет команды townyresearch");direct.setExecutor(command);direct.setTabCompleter(command);
        townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"research",command);if(!townCommand)getLogger().warning("/t research занят; используйте /townyresearch");
        getServer().getPluginManager().registerEvents(menu,this);getServer().getPluginManager().registerEvents(new ru.neverland.townyresearch.integration.WallProtection(service),this);service.start();getServer().getServicesManager().register(TownyResearchApi.class,service,this,ServicePriority.Normal);
        if(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")){expansion=new ResearchExpansion(service,getPluginMeta().getVersion());expansion.register();}
        getLogger().info("Исследования включены: "+service.settings().technologies().size()+" профилей.");
    }catch(Exception|LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Исследования не загружены. Данные не перезаписаны; исследования приостановлены до исправления причины.",ex);getServer().getPluginManager().disablePlugin(this);}}
    private YamlConfiguration read(String name)throws Exception{var y=new YamlConfiguration();y.load(new File(getDataFolder(),name));try(var input=getResource(name)){if(input!=null)y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(input,StandardCharsets.UTF_8)));}y.options().copyDefaults(true);return y;}
    private ResearchSettings settings()throws Exception{return ResearchSettings.load(read("config.yml"),read("technologies.yml"));}
    public boolean reloadResearch(){try{service.reload(settings());return true;}catch(Exception ex){getLogger().warning("Настройки исследований не применены: "+ex.getMessage());return false;}}
    @Override public void onDisable(){if(expansion!=null)expansion.unregister();if(service!=null)service.stop();getServer().getServicesManager().unregisterAll(this);if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"research");}
}
