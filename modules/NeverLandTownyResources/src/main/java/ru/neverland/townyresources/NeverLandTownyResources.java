package ru.neverland.townyresources;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townyresources.api.TownyResourcesApi;
import ru.neverland.townyresources.command.ResourcesCommand;
import ru.neverland.townyresources.config.ResourcesSettings;
import ru.neverland.townyresources.data.ResourcesRepository;
import ru.neverland.townyresources.gui.ResourcesMenu;
import ru.neverland.townyresources.integration.ResourcesExpansion;
import ru.neverland.townyresources.service.ResourcesService;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class NeverLandTownyResources extends JavaPlugin {
    private ResourcesService service;private boolean townCommand;private Runnable removePlaceholder;
    @Override public void onEnable(){try{
        saveDefaultConfig();if(!new File(getDataFolder(),"buildings.yml").exists())saveResource("buildings.yml",false);
        var settings=readSettings();var repository=new ResourcesRepository(getDataFolder().toPath().resolve("resources-data.yml"));repository.load();
        service=new ResourcesService(this,repository,settings);var menu=new ResourcesMenu(this,service);var command=new ResourcesCommand(this,service,menu);var direct=getCommand("townyresources");if(direct==null)throw new IllegalStateException("Нет команды townyresources");direct.setExecutor(command);direct.setTabCompleter(command);
        townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"resources",command);if(!townCommand)getLogger().warning("/t resources занят; используйте /townyresources");
        getServer().getPluginManager().registerEvents(menu,this);service.start();getServer().getServicesManager().register(TownyResourcesApi.class,service,this,ServicePriority.Normal);
        if(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI"))try{var expansion=new ResourcesExpansion(service,getPluginMeta().getVersion());if(expansion.register())removePlaceholder=expansion::unregister;}catch(LinkageError ex){getLogger().warning("PlaceholderAPI несовместим: "+ex.getMessage());}
        getLogger().info("Городские ресурсы включены: восемь запасов, "+settings.buildings().size()+" профилей.");
    }catch(Exception|LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Городские ресурсы не загружены; данные сохранены без перезаписи",ex);getServer().getPluginManager().disablePlugin(this);}}
    private YamlConfiguration read(String name)throws Exception{
        var yaml=new YamlConfiguration();yaml.load(new File(getDataFolder(),name));try(var input=getResource(name)){if(input!=null)yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(input,StandardCharsets.UTF_8)));}yaml.options().copyDefaults(true);return yaml;
    }
    private ResourcesSettings readSettings()throws Exception{return ResourcesSettings.load(read("config.yml"),read("buildings.yml"));}
    public boolean reloadResources(){try{service.reload(readSettings());return true;}catch(Exception ex){getLogger().warning("Настройки ресурсов не применены: "+ex.getMessage());return false;}}
    @Override public void onDisable(){if(service!=null)service.stop();if(removePlaceholder!=null)removePlaceholder.run();getServer().getServicesManager().unregisterAll(this);if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"resources");}
}
