package ru.neverland.townysieges;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import ru.neverland.townysieges.api.TownySiegesApi;

public final class NeverLandTownySiegesPlus extends JavaPlugin {
    private SiegeService service;private BukkitTask task;private boolean townCommand;
    private RaidController raids;private TntRaidEngine tnt;
    private RaidLoot loot;private BukkitTask lootTask;
    private RaidCamps camps;
    private RaidRepairs repairs;
    public RaidRepairs repairs(){return repairs;}
    public RaidCamps camps(){return camps;}
    public RaidLoot loot(){return loot;}
    private YamlConfiguration configuration()throws Exception{var yaml=new YamlConfiguration();yaml.load(new File(getDataFolder(),"config.yml"));try(var stream=getResource("config.yml")){yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8)));}return yaml;}
    public RaidController raids(){return raids;}
    public TntRaidEngine tnt(){return tnt;}
    public void reloadSieges()throws Exception{ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {var config=configuration();var next=SiegeSettings.load(config);var nextTnt=TntSettings.load(config);var nextLoot=LootSettings.load(config);var nextCamp=CampSettings.load(config);var nextDefense=LootDefense.load(config);service.reload(next);raids.reload(nextTnt);loot.reload(nextLoot);loot.defense(nextDefense);camps.reload(nextCamp);reloadConfig();} }
    @Override public void onEnable(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;
try{saveDefaultConfig();var config=configuration();service=new SiegeService(this,SiegeSettings.load(config));raids=new RaidController(this,service,TntSettings.load(config));loot=new RaidLoot(this,raids,LootSettings.load(config),service,LootDefense.load(config));camps=new RaidCamps(this,raids,CampSettings.load(config));repairs=new RaidRepairs(this,raids);getServer().getPluginManager().registerEvents(camps,this);tnt=new TntRaidEngine(this,raids,service);raids.engine(tnt);service.raids(raids);var menu=new SiegeMenu(this,service);var command=new SiegeCommand(this,service,menu);getCommand("townysieges").setExecutor(command);getCommand("townysieges").setTabCompleter(command);getServer().getPluginManager().registerEvents(menu,this);getServer().getPluginManager().registerEvents(new SiegeListener(service),this);getServer().getPluginManager().registerEvents(tnt,this);townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"sieges",command);if(!townCommand)getLogger().warning("/t sieges занят; используйте /sieges");getServer().getServicesManager().register(TownySiegesApi.class,service,this,ServicePriority.Normal);task=ru.neverland.core.SuiteScheduler.get().runTaskTimer(this,service::pulse,20,20);lootTask=ru.neverland.core.SuiteScheduler.get().runTaskTimer(this,()->{loot.pulse();camps.pulse();repairs.pulse();},20,20);service.war().connect();getLogger().info("Sieges+: "+service.war().detail()+"; армейские рейды и прочность TNT включены по настройкам");}catch(Exception|LinkageError e){getLogger().log(java.util.logging.Level.SEVERE,"Sieges+ не загружен: проверьте конфигурацию",e);getServer().getPluginManager().disablePlugin(this);}} }
    @Override public void onDisable(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        if (!ru.neverland.core.ModuleLifecycle.end(this)) return;
if(task!=null)task.cancel();if(lootTask!=null)lootTask.cancel();if(raids!=null){raids.close();if(org.bukkit.Bukkit.isStopping())try{raids.store().stopped().toCompletableFuture().get(2,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception storageError){getLogger().warning("Остановка: сохранение хранилища рейдов ещё не завершено: "+storageError.getMessage());}}if(service!=null)service.stop();getServer().getServicesManager().unregisterAll(this);if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"sieges");} }
}
