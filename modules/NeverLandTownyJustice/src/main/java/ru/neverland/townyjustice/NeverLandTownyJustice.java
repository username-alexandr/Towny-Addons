package ru.neverland.townyjustice;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import ru.neverland.townyjustice.api.TownyJusticeApi;
public final class NeverLandTownyJustice extends JavaPlugin {
    private JusticeService service;private JusticeDossiersService dossiers;private boolean townCommand;private JusticeExpansion expansion;private BukkitTask task;
    private JusticeSettings settings()throws Exception{var config=new YamlConfiguration();config.load(new File(getDataFolder(),"config.yml"));try(var stream=getResource("config.yml")){config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8)));}return JusticeSettings.load(config);}
    public void reloadJustice()throws Exception{ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {service.reload(settings());reloadConfig();} }
    @Override public void onEnable(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;
try{saveDefaultConfig();var repository=new JusticeRepository(getDataFolder().toPath().resolve("justice-data.yml"));repository.load();service=new JusticeService(this,repository,settings());var dossierStore=new JusticeDossiersRepository(getDataFolder().toPath().resolve("dossiers.yml"));try{dossierStore.load();}catch(Exception error){getLogger().log(java.util.logging.Level.WARNING,"Материалы и апелляции недоступны; исходные дела и платежи сохранены",error);}dossiers=new JusticeDossiersService(this,service,dossierStore);service.dossiers(dossiers);ru.neverland.core.ModuleShutdown.register(this,dossiers::prepareClose);getServer().getPluginManager().registerEvents(new JusticeDossiersMenu(this,service),this);var menu=new JusticeMenu(this,service);var command=new JusticeCommand(this,service,menu);getCommand("townyjustice").setExecutor(command);getCommand("townyjustice").setTabCompleter(command);getServer().getPluginManager().registerEvents(menu,this);
        townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"justice",command);if(!townCommand)getLogger().warning("/t justice занят; используйте /justice");task=ru.neverland.core.SuiteScheduler.get().runTaskTimer(this,service::pulse,40L,40L);getServer().getServicesManager().register(TownyJusticeApi.class,service,this,ServicePriority.Normal);
        if(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")){expansion=new JusticeExpansion(service,getPluginMeta().getVersion());expansion.register();}
    }catch(Exception|LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Суд не загружен; данные не перезаписаны",ex);getServer().getPluginManager().disablePlugin(this);}} }
    @Override public void onDisable(){try{try(var guard=ru.neverland.core.SuiteState.guard()){
        if(!ru.neverland.core.ModuleLifecycle.end(this))return;
        if(task!=null)task.cancel();if(expansion!=null)expansion.unregister();getServer().getServicesManager().unregisterAll(this);if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"justice");
        ru.neverland.core.SuiteScheduler.cleanupPlayers(this,p->{var holder=p.getOpenInventory().getTopInventory().getHolder();if(holder instanceof JusticeDossiersMenu.Holder||JusticeMenu.owned(holder))p.closeInventory();});
    }}finally{if(dossiers!=null){dossiers.prepareClose();dossiers.awaitClosed();}ru.neverland.core.ModuleShutdown.remove(this);}}
}
