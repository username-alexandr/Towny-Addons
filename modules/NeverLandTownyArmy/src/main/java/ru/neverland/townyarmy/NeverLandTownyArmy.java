package ru.neverland.townyarmy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townyarmy.api.TownyArmyApi;
import ru.neverland.townyarmy.tactics.*;

public final class NeverLandTownyArmy extends JavaPlugin {
    private ArmyService service;
    private ArmyRepository repository;
    private ArmyExpansion expansion;
    private TacticsRepository tacticsRepository;private TacticsService tactics;private TacticsMenus tacticsMenus;private BukkitTask tacticsTask;
    public TacticsService tactics(){return tactics;}
    public TacticsMenus tacticsMenus(){return tacticsMenus;}
    private BukkitTask task;
    private YamlConfiguration configuration() throws Exception {
        var yaml = new YamlConfiguration(); yaml.load(new File(getDataFolder(), "config.yml"));
        try (var stream = getResource("config.yml")) { yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8))); }
        return yaml;
    }
    private ArmySettings settings(YamlConfiguration yaml)throws Exception{
        var settings = ArmySettings.load(yaml);
        for (var unit : settings.units().values()) if (!unit.icon().isItem()) throw new IllegalArgumentException("Иконка подразделения должна быть предметом");
        return settings;
    }
    public void reloadArmy() throws Exception { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { var config=configuration();var next=settings(config);var extra=TacticsSettings.load(config);if(tactics!=null&&tactics.busy())throw new IllegalArgumentException("Дождитесь сохранения военного журнала");service.reload(next);if(tactics!=null)tactics.reload(extra);reloadConfig();service.refresh(); } }
    @Override public void onEnable() {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;

        try {
            saveDefaultConfig();var config=configuration();var settings=settings(config);
            repository = new ArmyRepository(getDataFolder().toPath().resolve("army-data.yml"), settings.auditLimit());
            ru.neverland.core.ModuleShutdown.register(this,this::prepareClose);repository.load();
            service = new ArmyService(this, repository, settings); service.migrate();
            tacticsRepository=new TacticsRepository(getDataFolder().toPath().resolve("army-tactics.yml"));
            try{var extra=TacticsSettings.load(config);tacticsRepository.load();tactics=new TacticsService(this,service,tacticsRepository,extra);tacticsMenus=new TacticsMenus(tactics);getServer().getPluginManager().registerEvents(tacticsMenus,this);tacticsTask=ru.neverland.core.SuiteScheduler.get().runTaskTimer(this,tactics::pulse,20,20);}catch(Exception|LinkageError error){getLogger().log(java.util.logging.Level.WARNING,"Специальности и выдача комплектов недоступны; основной состав армии работает",error);}
            var menu = new ArmyMenu(this, service); var command = new ArmyCommand(this, service, menu);
            getCommand("townymilitary").setExecutor(command); getCommand("townymilitary").setTabCompleter(command);
            getServer().getPluginManager().registerEvents(menu, this);
            getServer().getPluginManager().registerEvents(new ArmyListener(service), this);
            getServer().getServicesManager().register(TownyArmyApi.class, service, this, ServicePriority.Normal);
            task = ru.neverland.core.SuiteScheduler.get().runTaskTimer(this, service::pulse, 5L, 5L);
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) { expansion = new ArmyExpansion(service, getPluginMeta().getVersion()); expansion.register(); }
            // Builds owns /t army and /townyarmy and forwards to this module without a loading cycle.
        } catch (Exception | LinkageError ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Армия не загружена; проверьте данные и версии Builds/Resources", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    private java.util.concurrent.CompletionStage<Void> prepareClose(){
        var extra=tactics!=null?tactics.close():tacticsRepository!=null?tacticsRepository.close():java.util.concurrent.CompletableFuture.<Void>completedFuture(null);
        return extra.handle((v,error)->error).thenCompose(error->{var core=service!=null?service.stop():repository!=null?repository.close():java.util.concurrent.CompletableFuture.<Void>completedFuture(null);return core.thenCompose(v->error==null?java.util.concurrent.CompletableFuture.<Void>completedFuture(null):java.util.concurrent.CompletableFuture.<Void>failedFuture(error));});
    }
    @Override public void onDisable() {java.util.concurrent.CompletionStage<Void> closing=java.util.concurrent.CompletableFuture.completedFuture(null);try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        if (!ru.neverland.core.ModuleLifecycle.end(this)) return;

        if (task != null) task.cancel();if(tacticsTask!=null)tacticsTask.cancel();closing=prepareClose();
        if (expansion != null) expansion.unregister(); getServer().getServicesManager().unregisterAll(this);
    }
        var pending=closing.toCompletableFuture();if(ru.neverland.core.SuiteState.held()&&!pending.isDone()){pending.whenCompleteAsync((v,error)->{if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"Сохранение армии при остановке требует проверки",error);ru.neverland.core.ModuleShutdown.remove(this);});return;}
        try{pending.get(10,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();getLogger().log(java.util.logging.Level.SEVERE,"Сохранение армии прервано",error);}catch(Exception error){getLogger().log(java.util.logging.Level.SEVERE,"Сохранение армии не подтверждено",error);}finally{ru.neverland.core.ModuleShutdown.remove(this);}
    }
}
