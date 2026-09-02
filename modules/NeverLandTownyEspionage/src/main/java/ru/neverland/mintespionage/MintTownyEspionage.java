package ru.neverland.mintespionage;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintespionage.api.MintTownyEspionageApi;
import ru.neverland.mintespionage.command.AdminCommand;
import ru.neverland.mintespionage.command.TownEspionageCommand;
import ru.neverland.mintespionage.gui.EspionageMenuManager;
import ru.neverland.mintespionage.integration.BuildBridge;
import ru.neverland.mintespionage.integration.ItemsAdderHook;
import ru.neverland.mintespionage.integration.PlaceholderHook;
import ru.neverland.mintespionage.integration.TownyHook;
import ru.neverland.mintespionage.service.DefinitionRegistry;
import ru.neverland.mintespionage.service.EconomyService;
import ru.neverland.mintespionage.service.EspionageApiService;
import ru.neverland.mintespionage.service.EspionageRepository;
import ru.neverland.mintespionage.service.EspionageService;
import ru.neverland.mintespionage.service.MessageService;

import java.io.File;
import java.util.List;

public final class MintTownyEspionage extends JavaPlugin {
    private static final List<String> TOWN_COMMANDS=List.of("spy","espionage","intelligence");
    private TownyHook towny;private ItemsAdderHook itemsAdder;private MessageService messages;private DefinitionRegistry definitions;private EspionageRepository repository;private EspionageService service;
    @Override public void onEnable(){
        ru.neverland.mintespionage.util.LegacyDataMigrator.migrate(this,"MintTownyEspionage");saveDefaultConfig();copy("messages.yml");copy("operations.yml");towny=new TownyHook(this);itemsAdder=new ItemsAdderHook();messages=new MessageService(this);definitions=new DefinitionRegistry(this);repository=new EspionageRepository(this);repository.load();
        service=new EspionageService(this,towny,definitions,repository,new BuildBridge(this),new EconomyService(this),messages);EspionageMenuManager menus=new EspionageMenuManager(this,towny,service,itemsAdder,messages);getServer().getPluginManager().registerEvents(menus,this);
        TownEspionageCommand townCommand=new TownEspionageCommand(towny,service,menus,messages);PluginCommand direct=getCommand("espionage");if(direct!=null){direct.setExecutor(townCommand);direct.setTabCompleter(townCommand);}for(String name:TOWN_COMMANDS)if(!towny.register(name,townCommand))getLogger().warning("Не удалось зарегистрировать /t "+name+"; используйте /espionage.");
        PluginCommand admin=getCommand("townyespionage");if(admin!=null){AdminCommand executor=new AdminCommand(this,towny,service,messages);admin.setExecutor(executor);admin.setTabCompleter(executor);}getServer().getServicesManager().register(MintTownyEspionageApi.class,new EspionageApiService(towny,service),this, ServicePriority.Normal);
        boolean papi=PlaceholderHook.register(this,towny,service);service.startScheduler();long autosave=Math.max(20,getConfig().getLong("scheduler.autosave-seconds",60)*20);getServer().getScheduler().runTaskTimer(this,repository::saveIfDirty,autosave,autosave);
        getLogger().info("NeverLandTownyEspionage 0.1.2 включён: операций "+definitions.all().size()+", активных "+repository.operations().stream().filter(v->v.status().name().equals("ACTIVE")).count()+", PlaceholderAPI="+papi+".");
    }
    @Override public void onDisable(){if(service!=null)service.shutdown();if(towny!=null)for(String name:TOWN_COMMANDS)towny.unregister(name);getServer().getServicesManager().unregisterAll(this);}
    public void reloadPlugin(){reloadConfig();messages.reload();itemsAdder.reload();definitions.reload();service.startScheduler();}
    private void copy(String name){if(!new File(getDataFolder(),name).exists())saveResource(name,false);}
}
