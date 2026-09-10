package ru.neverland.townyspecialization;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townyspecialization.api.TownySpecializationApi;
import ru.neverland.townyspecialization.config.SpecializationSettings;
import ru.neverland.townyspecialization.data.SpecializationRepository;
import ru.neverland.townyspecialization.service.SpecializationService;
import ru.neverland.townyspecialization.command.SpecializationCommand;
import ru.neverland.townyspecialization.gui.SpecializationMenu;
import ru.neverland.townyspecialization.integration.SpecializationExpansion;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class NeverLandTownySpecialization extends JavaPlugin {
    private SpecializationService service;private boolean townCommand;private SpecializationExpansion expansion;
    @Override public void onEnable(){try{
        saveDefaultConfig();if(!new File(getDataFolder(),"specializations.yml").exists())saveResource("specializations.yml",false);
        var repository=new SpecializationRepository(getDataFolder().toPath().resolve("specialization-data.yml"));repository.load();service=new SpecializationService(this,repository,settings());
        var menu=new SpecializationMenu(this,service);var command=new SpecializationCommand(this,service,menu);var direct=getCommand("townyspecialization");if(direct==null)throw new IllegalStateException("Нет команды townyspecialization");direct.setExecutor(command);direct.setTabCompleter(command);
        townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"specialization",command);if(!townCommand)getLogger().warning("/t specialization занят; используйте /townyspecialization");
        getServer().getPluginManager().registerEvents(menu,this);getServer().getPluginManager().registerEvents(new ru.neverland.townyspecialization.integration.FortressProtection(service),this);getServer().getServicesManager().register(TownySpecializationApi.class,service,this,ServicePriority.Normal);service.start();
        if(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")){expansion=new SpecializationExpansion(service,getPluginMeta().getVersion());expansion.register();}
        getLogger().info("Специализации включены: "+service.settings().profiles().size()+" профилей.");
    }catch(Exception|LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Специализации не загружены; проверьте настройки и сохранённую базу.",ex);getServer().getPluginManager().disablePlugin(this);}}
    private YamlConfiguration read(String name)throws Exception{var y=new YamlConfiguration();y.load(new File(getDataFolder(),name));try(var input=getResource(name)){if(input!=null)y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(input,StandardCharsets.UTF_8)));}y.options().copyDefaults(true);return y;}
    private SpecializationSettings settings()throws Exception{return SpecializationSettings.load(read("config.yml"),read("specializations.yml"));}
    public boolean reloadSpecialization(){try{service.reload(settings());return true;}catch(Exception ex){getLogger().warning("Настройки специализаций не применены: "+ex.getMessage());return false;}}
    @Override public void onDisable(){if(expansion!=null)expansion.unregister();if(service!=null)service.stop();getServer().getServicesManager().unregisterAll(this);if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"specialization");}
}
