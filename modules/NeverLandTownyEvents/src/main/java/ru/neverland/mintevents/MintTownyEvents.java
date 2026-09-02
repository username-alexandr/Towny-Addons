package ru.neverland.mintevents;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintevents.api.MintTownyEventsApi;
import ru.neverland.mintevents.command.AdminCommand;
import ru.neverland.mintevents.command.TownEventsCommand;
import ru.neverland.mintevents.gui.EventMenuManager;
import ru.neverland.mintevents.integration.PlaceholderHook;
import ru.neverland.mintevents.integration.TownyHook;
import ru.neverland.mintevents.listener.EventGameplayListener;
import ru.neverland.mintevents.service.DevelopmentService;
import ru.neverland.mintevents.service.EventRegistry;
import ru.neverland.mintevents.service.EventRepository;
import ru.neverland.mintevents.service.EventService;
import ru.neverland.mintevents.service.MessageService;

import java.io.File;

public final class MintTownyEvents extends JavaPlugin {
    private TownyHook towny;
    private MessageService messages;
    private EventRegistry registry;
    private EventRepository repository;
    private DevelopmentService development;
    private EventService events;

    @Override
    public void onEnable() {
        ru.neverland.mintevents.util.LegacyDataMigrator.migrate(this, "MintTownyEvents");
        saveDefaultConfig();
        copyResource("messages.yml");
        copyResource("events.yml");

        towny = new TownyHook();
        messages = new MessageService(this);
        registry = new EventRegistry(this);
        repository = new EventRepository(this);
        repository.load();
        development = new DevelopmentService(this);
        events = new EventService(this, towny, registry, repository, development, messages);

        EventMenuManager menus = new EventMenuManager(this, towny, events, messages);
        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(new EventGameplayListener(this, towny, events), this);
        AdminCommand adminExecutor = new AdminCommand(this, towny, events, messages);
        towny.registerTownCommand("events", new TownEventsCommand(towny, menus, messages, adminExecutor));

        PluginCommand admin = getCommand("townyevents");
        if (admin != null) {
            admin.setExecutor(adminExecutor);
            admin.setTabCompleter(adminExecutor);
        }

        getServer().getServicesManager().register(MintTownyEventsApi.class, events, this, ServicePriority.Normal);
        boolean placeholders = PlaceholderHook.register(this, towny, events);
        events.start();
        getLogger().info("NeverLandTownyEvents 0.1.5 включён: загружено событий " + registry.all().size()
                + ", PlaceholderAPI=" + placeholders + ".");
    }

    @Override
    public void onDisable() {
        if (events != null) events.shutdown();
        if (towny != null) towny.unregisterTownCommand("events");
        getServer().getServicesManager().unregisterAll(this);
    }

    public void reloadPlugin() {
        reloadConfig();
        messages.reload();
        registry.reload();
        development.clearCache();
        events.reloadRuntime();
    }

    private void copyResource(String name) {
        if (!new File(getDataFolder(), name).exists()) saveResource(name, false);
    }
}
