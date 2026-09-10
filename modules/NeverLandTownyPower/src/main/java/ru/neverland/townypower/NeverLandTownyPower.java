package ru.neverland.townypower;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townypower.api.TownyPowerApi;
import ru.neverland.townypower.config.PowerSettings;
import ru.neverland.townypower.data.PowerRepository;
import ru.neverland.townypower.service.PowerService;
import ru.neverland.townypower.command.PowerCommand;
import ru.neverland.townypower.gui.PowerMenu;
import ru.neverland.townypower.integration.PowerExpansion;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class NeverLandTownyPower extends JavaPlugin {
    private PowerService service;private boolean townCommand;private PowerExpansion expansion;
    @Override public void onEnable(){try{
        saveDefaultConfig();if(!new File(getDataFolder(),"buildings.yml").exists())saveResource("buildings.yml",false);
        var repository=new PowerRepository(getDataFolder().toPath().resolve("power-data.yml"));repository.load();service=new PowerService(this,repository,settings());
        var menu=new PowerMenu(this,service);var command=new PowerCommand(this,service,menu);var direct=getCommand("townypower");if(direct==null)throw new IllegalStateException("Нет команды townypower");direct.setExecutor(command);direct.setTabCompleter(command);
        townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"power",command);if(!townCommand)getLogger().warning("/t power занят; используйте /townypower");
        getServer().getPluginManager().registerEvents(menu,this);service.start();getServer().getServicesManager().register(TownyPowerApi.class,service,this,ServicePriority.Normal);
        if(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")){expansion=new PowerExpansion(service,getPluginMeta().getVersion());expansion.register();}
        getLogger().info("Энергетика включена: "+service.settings().profiles().size()+" профилей.");
    }catch(Exception|LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Энергетика не загружена. Данные не перезаписаны; питание приостановлено до исправления причины.",ex);getServer().getPluginManager().disablePlugin(this);}}
    private YamlConfiguration read(String name)throws Exception{var y=new YamlConfiguration();y.load(new File(getDataFolder(),name));try(var input=getResource(name)){if(input!=null)y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(input,StandardCharsets.UTF_8)));}y.options().copyDefaults(true);return y;}
    private PowerSettings settings()throws Exception{return PowerSettings.load(read("config.yml"),read("buildings.yml"));}
    public boolean reloadPower(){try{service.reload(settings());return true;}catch(Exception ex){getLogger().warning("Настройки энергетики не применены: "+ex.getMessage());return false;}}
    @Override public void onDisable(){if(expansion!=null)expansion.unregister();if(service!=null)service.stop();getServer().getServicesManager().unregisterAll(this);if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"power");}
}
