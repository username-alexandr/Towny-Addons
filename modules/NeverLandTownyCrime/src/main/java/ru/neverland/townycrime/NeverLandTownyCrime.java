package ru.neverland.townycrime;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import ru.neverland.townycrime.api.TownyCrimeApi;
public final class NeverLandTownyCrime extends JavaPlugin {
    private CrimeService service;private CrimePatrolService patrols;private boolean townCommand;private CrimeExpansion expansion;
    private CrimeSettings settings()throws Exception{var config=new YamlConfiguration();config.load(new File(getDataFolder(),"config.yml"));try(var stream=getResource("config.yml")){config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8)));}config.options().copyDefaults(true);return CrimeSettings.load(config);}
    public void reloadCrime()throws Exception{ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {service.reload(settings());reloadConfig();if(patrols!=null)patrols.reload();} }
    @Override public void onEnable(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;
try{saveDefaultConfig();var repository=new CrimeRepository(getDataFolder().toPath().resolve("crime-data.yml"));repository.load();service=new CrimeService(this,repository,settings());var patrolRepository=new CrimePatrolRepository(getDataFolder().toPath().resolve("patrol-data.yml"));try{patrolRepository.load();}catch(Exception error){getLogger().log(java.util.logging.Level.WARNING,"Патрули недоступны; расчёт преступности продолжается",error);}patrols=new CrimePatrolService(this,patrolRepository);service.patrols(patrols);ru.neverland.core.ModuleShutdown.register(this,patrols::prepareClose);var menu=new CrimeMenu(this,service);var patrolMenu=new CrimePatrolMenu(this,patrols,menu);menu.patrolMenu(patrolMenu);getServer().getPluginManager().registerEvents(patrolMenu,this);var command=new CrimeCommand(this,menu,patrols,patrolMenu);getCommand("townycrime").setExecutor(command);getCommand("townycrime").setTabCompleter(command);getServer().getPluginManager().registerEvents(menu,this);
        townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"crime",command);if(!townCommand)getLogger().warning("/t crime занят; используйте /townycrime");service.start();getServer().getServicesManager().register(TownyCrimeApi.class,service,this,ServicePriority.Normal);
        if(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")){expansion=new CrimeExpansion(service,getPluginMeta().getVersion());expansion.register();}
    }catch(Exception|LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Преступность не загружена; данные не перезаписаны",ex);getServer().getPluginManager().disablePlugin(this);}} }
    @Override public void onDisable(){try{try(var guard=ru.neverland.core.SuiteState.guard()){
        if(!ru.neverland.core.ModuleLifecycle.end(this))return;
        if(expansion!=null)expansion.unregister();if(service!=null)service.stop();ru.neverland.core.SuiteScheduler.cleanupPlayers(this,p->{var inventory=p.getOpenInventory().getTopInventory();if(CrimeMenu.holds(inventory)||CrimePatrolMenu.holds(inventory))p.closeInventory();});getServer().getServicesManager().unregisterAll(this);if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"crime");
    }}finally{if(patrols!=null){patrols.prepareClose();patrols.awaitClosed();}ru.neverland.core.ModuleShutdown.remove(this);}}
}
