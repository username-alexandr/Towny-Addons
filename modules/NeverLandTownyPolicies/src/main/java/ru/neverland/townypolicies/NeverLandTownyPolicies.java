package ru.neverland.townypolicies;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townypolicies.api.TownyPoliciesApi;
import ru.neverland.townypolicies.config.PoliciesSettings;
import ru.neverland.townypolicies.data.PoliciesRepository;
import ru.neverland.townypolicies.service.PoliciesService;
import ru.neverland.townypolicies.command.PoliciesCommand;
import ru.neverland.townypolicies.gui.PoliciesMenu;
import ru.neverland.townypolicies.integration.PoliciesExpansion;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class NeverLandTownyPolicies extends JavaPlugin {
    private PoliciesService service;private boolean townCommand;private PoliciesExpansion expansion;
    @Override public void onEnable(){try{
        saveDefaultConfig();if(!new File(getDataFolder(),"policies.yml").exists())saveResource("policies.yml",false);
        var repository=new PoliciesRepository(getDataFolder().toPath().resolve("policies-data.yml"));repository.load();service=new PoliciesService(this,repository,settings());
        var menu=new PoliciesMenu(this,service);var command=new PoliciesCommand(this,service,menu);var direct=getCommand("townypolicies");if(direct==null)throw new IllegalStateException("Нет команды townypolicies");direct.setExecutor(command);direct.setTabCompleter(command);
        townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"policies",command);if(!townCommand)getLogger().warning("/t policies занят; используйте /townypolicies");
        getServer().getPluginManager().registerEvents(menu,this);getServer().getServicesManager().register(TownyPoliciesApi.class,service,this,ServicePriority.Normal);service.start();
        if(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")){expansion=new PoliciesExpansion(service,getPluginMeta().getVersion());expansion.register();}
        getLogger().info("Политики включены: "+service.settings().groups().size()+" профилей.");
    }catch(Exception|LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Политики не загружены; проверьте настройки и сохранённую базу.",ex);getServer().getPluginManager().disablePlugin(this);}}
    private YamlConfiguration read(String name)throws Exception{var y=new YamlConfiguration();y.load(new File(getDataFolder(),name));try(var input=getResource(name)){if(input!=null)y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(input,StandardCharsets.UTF_8)));}y.options().copyDefaults(true);return y;}
    private PoliciesSettings settings()throws Exception{return PoliciesSettings.load(read("config.yml"),read("policies.yml"));}
    public boolean reloadPolicies(){try{service.reload(settings());return true;}catch(Exception ex){getLogger().warning("Настройки политик не применены: "+ex.getMessage());return false;}}
    @Override public void onDisable(){if(expansion!=null)expansion.unregister();if(service!=null)service.stop();getServer().getServicesManager().unregisterAll(this);if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"policies");}
}
