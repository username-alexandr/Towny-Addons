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
    private java.util.concurrent.CompletionStage<Void> closing;
    private RaidController raids;private TntRaidEngine tnt;
    private RaidLoot loot;private BukkitTask lootTask;
    private RaidCamps camps;
    private CampSupplies supplies;public CampSupplies supplies(){return supplies;}
    private RaidHours hours;public RaidHours hours(){return hours;}
    private RaidRepairs repairs;
    private WarControl warControl;
    public WarControl warControl(){return warControl;}
    public RaidRepairs repairs(){return repairs;}
    public RaidCamps camps(){return camps;}
    public RaidLoot loot(){return loot;}
    private YamlConfiguration configuration()throws Exception{var yaml=new YamlConfiguration();yaml.load(new File(getDataFolder(),"config.yml"));try(var stream=getResource("config.yml")){yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8)));}return yaml;}
    public RaidController raids(){return raids;}
    public TntRaidEngine tnt(){return tnt;}
    public void reloadSieges()throws Exception{ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {var config=configuration();var next=SiegeSettings.load(config);var nextTnt=TntSettings.load(config);var nextLoot=LootSettings.load(config);var nextCamp=CampSettings.load(config);var nextDefense=LootDefense.load(config);var nextWar=WarSettings.load(config);var nextSupply=CampSupplies.Settings.load(config);hours.reload(config);service.reload(next);raids.reload(nextTnt);loot.reload(nextLoot);loot.defense(nextDefense);camps.reload(nextCamp);warControl.reload(nextWar);supplies.reload(nextSupply);reloadConfig();} }
    @Override public void onEnable(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;
        ru.neverland.core.ModuleShutdown.register(this,this::prepareClose);
try{saveDefaultConfig();var config=configuration();service=new SiegeService(this,SiegeSettings.load(config));raids=new RaidController(this,service,TntSettings.load(config));hours=new RaidHours(this,config);loot=new RaidLoot(this,raids,LootSettings.load(config),service,LootDefense.load(config));camps=new RaidCamps(this,raids,CampSettings.load(config));supplies=new CampSupplies(this,raids,config);repairs=new RaidRepairs(this,raids);getServer().getPluginManager().registerEvents(camps,this);tnt=new TntRaidEngine(this,raids,service);raids.engine(tnt);warControl=new WarControl(this,raids,WarSettings.load(config));getServer().getPluginManager().registerEvents(new WarTownyListener(this,warControl),this);getServer().getServicesManager().register(ru.neverland.townysieges.api.TownyWarChunkApi.class,warControl,this,ServicePriority.Normal);service.raids(raids);var menu=new SiegeMenu(this,service);var command=new SiegeCommand(this,service,menu);getCommand("townysieges").setExecutor(command);getCommand("townysieges").setTabCompleter(command);getServer().getPluginManager().registerEvents(menu,this);getServer().getPluginManager().registerEvents(new SiegeListener(service),this);getServer().getPluginManager().registerEvents(tnt,this);townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"sieges",command);if(!townCommand)getLogger().warning("/t sieges занят; используйте /sieges");getServer().getServicesManager().register(TownySiegesApi.class,service,this,ServicePriority.Normal);task=ru.neverland.core.SuiteScheduler.get().runTaskTimer(this,service::pulse,20,20);lootTask=ru.neverland.core.SuiteScheduler.get().runTaskTimer(this,()->{raids.expire();loot.pulse();camps.pulse();supplies.pulse();repairs.pulse();warControl.pulse();},20,20);service.war().connect();getLogger().info("Sieges+: "+service.war().detail()+"; армейские рейды и прочность TNT включены по настройкам");}catch(Exception|LinkageError e){getLogger().log(java.util.logging.Level.SEVERE,"Sieges+ не загружен: проверьте конфигурацию",e);getServer().getPluginManager().disablePlugin(this);}} }
    private java.util.concurrent.CompletionStage<Void> prepareClose(){try(var guard=ru.neverland.core.SuiteState.guard()){
        if(closing!=null)return closing;if(task!=null)task.cancel();if(lootTask!=null)lootTask.cancel();if(warControl!=null)warControl.close();if(raids!=null)raids.close();if(service!=null)service.stop();
        closing=raids==null?java.util.concurrent.CompletableFuture.completedFuture(null):raids.store().stopped();return closing;
    }}
    @Override public void onDisable(){java.util.concurrent.CompletionStage<Void> pending;try(var guard=ru.neverland.core.SuiteState.guard()){
        if(!ru.neverland.core.ModuleLifecycle.end(this))return;pending=prepareClose();getServer().getServicesManager().unregisterAll(this);if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"sieges");
    }
        if(ru.neverland.core.SuiteState.held()&&!pending.toCompletableFuture().isDone()){pending.whenCompleteAsync((v,error)->{if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"Завершение сохранения рейдов требует проверки",error);ru.neverland.core.ModuleShutdown.remove(this);});return;}
        try{pending.toCompletableFuture().get(10,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();getLogger().log(java.util.logging.Level.SEVERE,"Сохранение рейдов прервано",error);}catch(Exception error){getLogger().log(java.util.logging.Level.SEVERE,"Сохранение рейдов не подтверждено",error);}finally{ru.neverland.core.ModuleShutdown.remove(this);}
    }
}
