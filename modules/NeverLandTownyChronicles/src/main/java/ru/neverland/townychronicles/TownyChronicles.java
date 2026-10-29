package ru.neverland.townychronicles;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townychronicles.api.TownyChroniclesApi;
import ru.neverland.townychronicles.command.AdminCommand;
import ru.neverland.townychronicles.command.ChroniclesCommand;
import ru.neverland.townychronicles.gui.ChronicleMenuManager;
import ru.neverland.townychronicles.integration.BuildBridge;
import ru.neverland.townychronicles.integration.DynamicWarBridge;
import ru.neverland.townychronicles.integration.PlaceholderHook;
import ru.neverland.townychronicles.integration.TownyHook;
import ru.neverland.townychronicles.listener.TownyHistoryListener;
import ru.neverland.townychronicles.service.ChronicleBookService;
import ru.neverland.townychronicles.service.ChronicleRepository;
import ru.neverland.townychronicles.service.ChronicleService;
import ru.neverland.townychronicles.service.ChronicleTracker;
import ru.neverland.townychronicles.service.MessageService;

import java.io.File;
import java.util.List;

public final class TownyChronicles extends JavaPlugin {
    private ru.neverland.townychronicles.service.ContributionsMenu contributions;private ru.neverland.townychronicles.chapters.ChapterMenu chapters;
    private static final List<String>TOWN_COMMANDS=List.of("chronicles","chronicle","history");private TownyHook towny;private MessageService messages;private ChronicleRepository repository;private ChronicleService chronicles;private ChronicleTracker tracker;
    @Override public void onEnable(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;
ru.neverland.townychronicles.util.LegacyDataMigrator.migrate(this,"TownyChronicles");saveDefaultConfig();copy("messages.yml");towny=new TownyHook();messages=new MessageService(this);repository=new ChronicleRepository(this);repository.load();chronicles=new ChronicleService(this,towny,repository);tracker=new ChronicleTracker(this,towny,new BuildBridge(this),repository,chronicles);ChronicleBookService books=new ChronicleBookService(this,repository);ChronicleMenuManager menus=new ChronicleMenuManager(this,towny,repository,books,messages);getServer().getPluginManager().registerEvents(menus,this);getServer().getPluginManager().registerEvents(new TownyHistoryListener(this,tracker),this);contributions=new ru.neverland.townychronicles.service.ContributionsMenu(this,towny,repository);chapters=new ru.neverland.townychronicles.chapters.ChapterMenu(this,towny,repository,contributions);ru.neverland.core.ModuleShutdown.register(this,()->java.util.concurrent.CompletableFuture.allOf(repository.prepareClose().toCompletableFuture(),contributions.prepareClose().toCompletableFuture(),chapters.prepareClose().toCompletableFuture()));ChroniclesCommand command=new ChroniclesCommand(towny,menus,books,messages,contributions,chapters);PluginCommand direct=getCommand("chronicles");if(direct!=null){direct.setExecutor(command);direct.setTabCompleter(command);}for(String name:TOWN_COMMANDS)if(!towny.register(name,command))getLogger().warning("Не удалось зарегистрировать /t "+name+"; используйте /chronicles.");PluginCommand admin=getCommand("townychronicles");if(admin!=null){AdminCommand executor=new AdminCommand(this,towny,chronicles,tracker,messages);admin.setExecutor(executor);admin.setTabCompleter(executor);}getServer().getServicesManager().register(TownyChroniclesApi.class,chronicles,this,ServicePriority.Normal);int warEvents=new DynamicWarBridge(this,chronicles).register();boolean papi=PlaceholderHook.register(this,towny,repository);tracker.start();long autosave=Math.max(20,getConfig().getLong("storage.autosave-seconds",60)*20L);ru.neverland.core.SuiteScheduler.get().runTaskTimer(this,repository::requestSave,autosave,autosave);getLogger().info("NeverLandTownyChronicles "+getPluginMeta().getVersion()+" включён: городских летописей "+repository.states().size()+", военных событий "+warEvents+", PlaceholderAPI="+papi+".");} }
    @Override public void onDisable(){
        try(var guard=ru.neverland.core.SuiteState.guard()){
            if(!ru.neverland.core.ModuleLifecycle.end(this))return;
            if(contributions!=null)contributions.prepareClose();if(chapters!=null)chapters.prepareClose();if(tracker!=null)tracker.stop();
            if(towny!=null)for(String name:TOWN_COMMANDS)towny.unregister(name);
            getServer().getServicesManager().unregisterAll(this);
        }
        if(contributions!=null)contributions.close();if(chapters!=null)chapters.close();if(repository!=null)repository.close();
    }
    public void reloadPlugin(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {reloadConfig();messages.reload();tracker.start();} }
    private void copy(String name){if(!new File(getDataFolder(),name).exists())saveResource(name,false);}
}
