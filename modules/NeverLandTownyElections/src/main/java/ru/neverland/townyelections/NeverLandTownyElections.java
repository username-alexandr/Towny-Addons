package ru.neverland.townyelections;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import ru.neverland.townyelections.api.TownyElectionsApi;

public final class NeverLandTownyElections extends JavaPlugin {
    private ElectionsService service;private ElectionProgramsService programs; private BukkitTask ticker; private boolean townCommand;
    @Override public void onEnable() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;

        try {
            saveDefaultConfig();var repository=new ElectionsRepository(getDataFolder().toPath().resolve("elections.yml"));repository.load();
            var settings=readSettings();ElectionsService.validateCatalog(settings);service=new ElectionsService(this,repository,settings);service.recover();
            var programRepository=new ElectionProgramsRepository(getDataFolder().toPath().resolve("programs.yml"));try{programRepository.load();}catch(Exception error){getLogger().log(java.util.logging.Level.WARNING,"Программы недоступны; выборы и бюллетени продолжают работать",error);}programs=new ElectionProgramsService(this,service,programRepository);service.programs(programs);ru.neverland.core.ModuleShutdown.register(this,programs::prepareClose);
            var menus=new ElectionsMenus(this,service);var command=new ElectionsCommand(this,service,menus);
            getCommand("elections").setExecutor(command);getCommand("elections").setTabCompleter(command);
            ru.neverland.core.ActivityAdmin.attach(this,"elections","townyelections.admin",service::adminTargets);
            townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"elections",command);
            if(!townCommand)getLogger().warning("/t elections занят; используйте /elections.");
            getServer().getPluginManager().registerEvents(menus,this);
            getServer().getServicesManager().register(TownyElectionsApi.class,service,this,ServicePriority.Normal);
            schedule();getLogger().info("Выборы мэра, совета и должностей включены. /t elections");
        } catch(Exception | LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Elections не загружен; журнал оставлен для восстановления",ex);getServer().getPluginManager().disablePlugin(this);}
    } }
    private ElectionsSettings readSettings() throws Exception {var y=new YamlConfiguration();y.load(getDataFolder().toPath().resolve("config.yml").toFile());return ElectionsSettings.load(y);}
    public void reloadSettings() throws Exception { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {var next=readSettings();service.settings(next);reloadConfig();schedule();} }
    private void schedule(){if(ticker!=null)ticker.cancel();ticker=ru.neverland.core.SuiteScheduler.get().runTaskTimer(this,service::tick,20,service.settings().checkTicks());}
    public ElectionsService elections(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return service;} }
    @Override public void onDisable(){ try {try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        if (!ru.neverland.core.ModuleLifecycle.end(this)) return;
if(ticker!=null)ticker.cancel();getServer().getServicesManager().unregisterAll(this);
        if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"elections");
        ru.neverland.core.SuiteScheduler.cleanupPlayers(this,p->{if(p.getOpenInventory().getTopInventory().getHolder() instanceof ElectionsMenus.Holder||p.getOpenInventory().getTopInventory().getHolder() instanceof ElectionProgramsMenu.View)p.closeInventory();});} }finally{if(programs!=null){programs.prepareClose();programs.awaitClosed();}ru.neverland.core.ModuleShutdown.remove(this);} }
}
