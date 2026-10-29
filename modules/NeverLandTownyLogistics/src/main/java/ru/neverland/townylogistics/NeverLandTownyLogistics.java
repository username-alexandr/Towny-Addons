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
import ru.neverland.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
public final class NeverLandTownyLogistics extends JavaPlugin {
    private LogisticsService service;private LogisticsRepository repository;private boolean registered;private volatile boolean stopping;private CompletionStage<Void> closed;
    private final CompletableFuture<Void> configClosed=new CompletableFuture<>();
    private final ExecutorService configWorker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{var t=new Thread(r,"NeverLand-logistics-config");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){configClosed.complete(null);}};
    @Override public void onEnable(){
        if(!ModuleLifecycle.begin(this))return;ModuleShutdown.register(this,this::prepareClose);
        var direct=getCommand("townylogistics");if(direct!=null)direct.setExecutor((sender,command,label,args)->{LogisticsCommand.tell(sender,"Логистика загружает сохранённые настройки и маршруты…");return true;});
        CompletableFuture.supplyAsync(()->{try{saveDefaultConfig();return settings();}catch(Exception error){throw new CompletionException(error);}},configWorker).whenComplete((settings,error)->{
            if(stopping||!isEnabled())return;try{SuiteScheduler.get().runTask(this,()->{if(stopping)return;if(error!=null){getLogger().log(java.util.logging.Level.SEVERE,"Настройки логистики не загружены; данные и груз сохранены",error);disableAfterFailure();return;}initialize(settings);});}catch(RuntimeException rejected){if(!stopping)getLogger().log(java.util.logging.Level.WARNING,"Запуск логистики отложен",rejected);}
        });
    }
    private void initialize(LogisticsSettings settings){try{
        repository=new LogisticsRepository(getDataFolder().toPath().resolve("logistics-data.yml"));service=new LogisticsService(this,repository,new BuildsStorage(),settings,new LogisticsRepository.Snapshot(Map.of(),Map.of()));
        var command=new LogisticsCommand(this,service);var menu=new LogisticsMenu(this,service,command);command.menu(menu);var direct=getCommand("townylogistics");if(direct==null)throw new IllegalStateException("Команда не объявлена");direct.setExecutor(command);direct.setTabCompleter(command);
        ActivityAdmin.attach(this,"townylogistics","neverlandtownylogistics.admin",service::adminTargets);registered=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"logistics",command);if(!registered)getLogger().warning("/t logistics занят; используйте /townylogistics.");
        getServer().getPluginManager().registerEvents(menu,this);service.start();getLogger().info("Логистика загружает сохранённые маршруты в фоне.");
    }catch(Exception|LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Логистика не загрузилась; данные и груз не сброшены",ex);disableAfterFailure();}}
    private void disableAfterFailure(){if(!stopping)org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(this,task->getServer().getPluginManager().disablePlugin(this),1);}
    private LogisticsSettings settings()throws Exception{var c=new YamlConfiguration();c.load(new File(getDataFolder(),"config.yml"));try(var stream=getResource("config.yml")){if(stream!=null)c.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8)));}c.options().copyDefaults(true);return LogisticsSettings.load(c);}
    public CompletionStage<Boolean> reloadLogistics(){if(stopping||service==null)return CompletableFuture.completedFuture(false);try{return CompletableFuture.supplyAsync(()->{try{return settings();}catch(Exception error){throw new CompletionException(error);}},configWorker).handle((settings,error)->{try(var guard=SuiteState.guard()){if(error!=null||stopping){if(error!=null)getLogger().warning("Настройки логистики не применены: "+error.getMessage());return false;}try{service.reload(settings);return true;}catch(RuntimeException unavailable){getLogger().warning(unavailable.getMessage());return false;}}});}catch(RejectedExecutionException busy){return CompletableFuture.completedFuture(false);}}
    private CompletionStage<Void> prepareClose(){try(var guard=SuiteState.guard()){if(closed!=null)return closed;stopping=true;configWorker.shutdown();closed=(service!=null?service.prepareClose():repository!=null?repository.prepareClose():CompletableFuture.<Void>completedFuture(null)).thenCombine(configClosed,(a,b)->null);return closed;}}
    @Override public void onDisable(){try{try(var guard=SuiteState.guard()){if(!ModuleLifecycle.end(this))return;if(registered)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"logistics");}prepareClose();}finally{try{var done=prepareClose().toCompletableFuture();if(SuiteState.held()&&!done.isDone())throw new IllegalStateException("Отключение логистики требует ожидания вне общей блокировки");done.get(10,TimeUnit.SECONDS);}catch(Exception error){getLogger().log(java.util.logging.Level.SEVERE,"Логистика ещё завершает запись; файлы сохранены для проверки",error);}finally{ModuleShutdown.remove(this);}}}
}
