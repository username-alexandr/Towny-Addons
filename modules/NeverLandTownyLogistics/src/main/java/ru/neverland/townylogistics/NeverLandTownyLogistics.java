package ru.neverland.townylogistics;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import ru.neverland.townylogistics.command.LogisticsCommand;
import ru.neverland.townylogistics.config.LogisticsSettings;
import ru.neverland.townylogistics.data.LogisticsRepository;
import ru.neverland.townylogistics.gui.LogisticsMenu;
import ru.neverland.townylogistics.integration.BuildsStorage;
import ru.neverland.townylogistics.service.LogisticsService;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class NeverLandTownyLogistics extends JavaPlugin {
    private LogisticsService service;private boolean registered;
    @Override public void onEnable(){try{
        saveDefaultConfig();var repository=new LogisticsRepository(getDataFolder().toPath().resolve("logistics-data.yml"));var state=repository.load();
        service=new LogisticsService(this,repository,new BuildsStorage(),settings(),state);var command=new LogisticsCommand(this,service);var menu=new LogisticsMenu(this,service,command);command.menu(menu);
        var direct=getCommand("townylogistics");if(direct==null)throw new IllegalStateException("Команда не объявлена");direct.setExecutor(command);direct.setTabCompleter(command);
        registered=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"logistics",command);if(!registered)getLogger().warning("/t logistics занят; используйте /townylogistics.");
        getServer().getPluginManager().registerEvents(menu,this);service.start();getLogger().info("Логистика с NPC-курьерами включена.");
    }catch(Exception|LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Логистика не загрузилась; данные и груз не сброшены",ex);getServer().getPluginManager().disablePlugin(this);}}
    private LogisticsSettings settings()throws Exception{var c=new YamlConfiguration();c.load(new File(getDataFolder(),"config.yml"));try(var stream=getResource("config.yml")){if(stream!=null)c.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8)));}c.options().copyDefaults(true);return LogisticsSettings.load(c);}
    public boolean reloadLogistics(){try{service.reload(settings());return true;}catch(Exception ex){getLogger().warning("Настройки логистики не применены: "+ex.getMessage());return false;}}
    @Override public void onDisable(){if(service!=null)service.stop();if(registered)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"logistics");}
}
