package ru.neverland.townytreasury;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townytreasury.api.TownyTreasuryApi;
import ru.neverland.townytreasury.config.TreasurySettings;
import ru.neverland.townytreasury.data.TreasuryRepository;
import ru.neverland.townytreasury.service.TreasuryService;
import ru.neverland.townytreasury.command.TreasuryCommand;
import ru.neverland.townytreasury.gui.TreasuryMenu;
import ru.neverland.townytreasury.integration.TreasuryExpansion;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class NeverLandTownyTreasuryPlus extends JavaPlugin {
    private TreasuryService service;private boolean townCommand;private TreasuryExpansion expansion;
    @Override public void onEnable(){try{saveDefaultConfig();var repository=new TreasuryRepository(getDataFolder().toPath().resolve("treasury-data.yml"));repository.load();service=new TreasuryService(this,repository,settings());var menu=new TreasuryMenu(this,service);var command=new TreasuryCommand(this,service,menu);var direct=getCommand("townytreasury");if(direct==null)throw new IllegalStateException("Нет команды townytreasury");direct.setExecutor(command);direct.setTabCompleter(command);townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"treasury",command);if(!townCommand)getLogger().warning("/t treasury занят; используйте /townytreasury");getServer().getPluginManager().registerEvents(menu,this);getServer().getServicesManager().register(TownyTreasuryApi.class,service,this,ServicePriority.Normal);service.start();if(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")){expansion=new TreasuryExpansion(service,getPluginMeta().getVersion());expansion.register();}getLogger().info("Казна и недельные отчёты включены.");}catch(Exception|LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Казна не загружена; проверьте настройки и сохранённую базу.",ex);getServer().getPluginManager().disablePlugin(this);}}
    private TreasurySettings settings()throws Exception{var y=new YamlConfiguration();y.load(new File(getDataFolder(),"config.yml"));try(var input=getResource("config.yml")){if(input!=null)y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(input,StandardCharsets.UTF_8)));}y.options().copyDefaults(true);return TreasurySettings.load(y);}
    public boolean reloadTreasury(){try{service.reload(settings());return true;}catch(Exception ex){getLogger().warning("Настройки не применены: "+ex.getMessage());return false;}}
    @Override public void onDisable(){if(expansion!=null)expansion.unregister();if(service!=null)service.stop();getServer().getServicesManager().unregisterAll(this);if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"treasury");}
}
