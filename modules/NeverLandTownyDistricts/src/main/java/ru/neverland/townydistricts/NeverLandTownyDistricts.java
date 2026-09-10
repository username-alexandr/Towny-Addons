package ru.neverland.townydistricts;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.Coord;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townydistricts.api.TownyDistrictsApi;
import ru.neverland.townydistricts.command.DistrictCommand;
import ru.neverland.townydistricts.config.DistrictSettings;
import ru.neverland.townydistricts.data.DistrictRepository;
import ru.neverland.townydistricts.gui.DistrictMenu;
import ru.neverland.townydistricts.listener.ProductionListener;
import ru.neverland.townydistricts.service.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class NeverLandTownyDistricts extends JavaPlugin{
    private DistrictService service;private BorderPreview borders;private boolean registered;
    @Override public void onEnable(){try{
        ru.neverland.townydistricts.integration.BuildsBridge.verify();
        saveDefaultConfig();if(!new File(getDataFolder(),"projects.yml").exists())saveResource("projects.yml",false);
        var settings=DistrictSettings.load(read("config.yml"),read("projects.yml"));
        var repository=new DistrictRepository(getDataFolder().toPath().resolve("districts-data.yml"),Coord.getCellSize());repository.load();
        service=new DistrictService(this,repository,settings);service.start();borders=new BorderPreview(this,service);
        var command=new DistrictCommand(this,service,borders);var menu=new DistrictMenu(this,service,command);command.menu(menu);
        var direct=getCommand("townydistricts");if(direct==null)throw new IllegalStateException("Команда не объявлена");direct.setExecutor(command);direct.setTabCompleter(command);
        registered=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"district",command);
        if(!registered)getLogger().warning("/t district занят; используйте /townydistricts.");
        getServer().getPluginManager().registerEvents(command,this);
        getServer().getPluginManager().registerEvents(service,this);getServer().getPluginManager().registerEvents(menu,this);
        getServer().getPluginManager().registerEvents(new ProductionListener(service),this);
        getServer().getServicesManager().register(TownyDistrictsApi.class,service,this,ServicePriority.Normal);
        getLogger().info("NeverLandTownyDistricts "+getPluginMeta().getVersion()+": семь типов районов включены.");
    }catch(Exception|LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Не удалось загрузить районы; база не сброшена.",ex);getServer().getPluginManager().disablePlugin(this);}}
    public boolean reloadDistricts(){try{var settings=DistrictSettings.load(read("config.yml"),read("projects.yml"));service.reload(settings);return true;}
        catch(Exception ex){getLogger().warning("Настройки районов не применены: "+ex.getMessage());return false;}}
    private YamlConfiguration read(String name)throws Exception{var y=new YamlConfiguration();y.load(new File(getDataFolder(),name));
        try(var stream=getResource(name)){if(stream!=null)y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8)));}y.options().copyDefaults(true);return y;}
    @Override public void onDisable(){if(service!=null)service.stop();if(borders!=null)borders.stop();if(registered)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"district");getServer().getServicesManager().unregisterAll(this);}
}
