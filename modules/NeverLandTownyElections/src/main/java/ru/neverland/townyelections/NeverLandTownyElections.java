package ru.neverland.townyelections;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import ru.neverland.townyelections.api.TownyElectionsApi;

public final class NeverLandTownyElections extends JavaPlugin {
    private ElectionsService service; private BukkitTask ticker; private boolean townCommand;
    @Override public void onEnable() {
        try {
            saveDefaultConfig();var repository=new ElectionsRepository(getDataFolder().toPath().resolve("elections.yml"));repository.load();
            var settings=readSettings();ElectionsService.validateCatalog(settings);service=new ElectionsService(this,repository,settings);service.recover();
            var menus=new ElectionsMenus(this,service);var command=new ElectionsCommand(this,service,menus);
            getCommand("elections").setExecutor(command);getCommand("elections").setTabCompleter(command);
            townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"elections",command);
            if(!townCommand)getLogger().warning("/t elections занят; используйте /elections.");
            getServer().getPluginManager().registerEvents(menus,this);
            getServer().getServicesManager().register(TownyElectionsApi.class,service,this,ServicePriority.Normal);
            schedule();getLogger().info("Выборы мэра, совета и должностей включены. /t elections");
        } catch(Exception | LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Elections не загружен; журнал оставлен для восстановления",ex);getServer().getPluginManager().disablePlugin(this);}
    }
    private ElectionsSettings readSettings() throws Exception {var y=new YamlConfiguration();y.load(getDataFolder().toPath().resolve("config.yml").toFile());return ElectionsSettings.load(y);}
    public void reloadSettings() throws Exception {var next=readSettings();service.settings(next);reloadConfig();schedule();}
    private void schedule(){if(ticker!=null)ticker.cancel();ticker=getServer().getScheduler().runTaskTimer(this,service::tick,20,service.settings().checkTicks());}
    public ElectionsService elections(){return service;}
    @Override public void onDisable(){if(ticker!=null)ticker.cancel();getServer().getServicesManager().unregisterAll(this);
        if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"elections");
        for(var p:getServer().getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof ElectionsMenus.Holder)p.closeInventory();}
}
