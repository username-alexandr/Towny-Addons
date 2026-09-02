package ru.neverland.archaeology;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.archaeology.api.TownyArchaeologyApi;
import ru.neverland.archaeology.command.AdminCommand;
import ru.neverland.archaeology.command.ArchaeologyCommand;
import ru.neverland.archaeology.command.TownMuseumCommand;
import ru.neverland.archaeology.gui.ArchaeologyMenuManager;
import ru.neverland.archaeology.integration.ItemsAdderHook;
import ru.neverland.archaeology.integration.PlaceholderHook;
import ru.neverland.archaeology.integration.TownyHook;
import ru.neverland.archaeology.service.ArchaeologyApiService;
import ru.neverland.archaeology.service.ArchaeologyRegistry;
import ru.neverland.archaeology.service.ArchaeologyRepository;
import ru.neverland.archaeology.service.ArtifactService;
import ru.neverland.archaeology.service.MessageService;
import ru.neverland.archaeology.service.MuseumService;
import ru.neverland.archaeology.service.SiteService;

import java.io.File;
import java.util.List;

public final class TownyArchaeology extends JavaPlugin {
    private static final List<String> USER_TOWN_COMMANDS = List.of("archaeology", "arch");

    private TownyHook towny;
    private ItemsAdderHook itemsAdder;
    private MessageService messages;
    private ArchaeologyRegistry registry;
    private ArchaeologyRepository repository;
    private ArtifactService artifacts;
    private MuseumService museums;
    private SiteService sites;
    private BukkitTask autosave;

    @Override
    public void onEnable() {
        ru.neverland.archaeology.util.LegacyDataMigrator.migrate(this, "TownyArchaeology");
        saveDefaultConfig();
        copy("messages.yml");
        copy("artifacts.yml");
        copy("sites.yml");
        towny = new TownyHook();
        itemsAdder = new ItemsAdderHook();
        messages = new MessageService(this);
        registry = new ArchaeologyRegistry(this);
        repository = new ArchaeologyRepository(this);
        repository.load();
        artifacts = new ArtifactService(this, registry, repository, itemsAdder, messages);
        museums = new MuseumService(this, towny, registry, repository, artifacts, messages);
        sites = new SiteService(this, towny, registry, repository, artifacts);
        ArchaeologyMenuManager menus = new ArchaeologyMenuManager(this, towny, registry, repository,
                artifacts, museums, itemsAdder, messages);
        getServer().getPluginManager().registerEvents(artifacts, this);
        getServer().getPluginManager().registerEvents(sites, this);
        getServer().getPluginManager().registerEvents(menus, this);

        ArchaeologyCommand userExecutor = new ArchaeologyCommand(registry, artifacts, museums, sites, menus, messages);
        PluginCommand user = getCommand("archaeology");
        if (user != null) {
            user.setExecutor(userExecutor);
            user.setTabCompleter(userExecutor);
        }
        for (String name : USER_TOWN_COMMANDS) {
            if (!towny.register(name, userExecutor)) {
                getLogger().warning("Не удалось зарегистрировать /t " + name + "; используйте /archaeology.");
            }
        }

        AdminCommand adminExecutor = new AdminCommand(this, towny, registry, artifacts, museums, sites, messages);
        PluginCommand admin = getCommand("townyarchaeology");
        if (admin != null) {
            admin.setExecutor(adminExecutor);
            admin.setTabCompleter(adminExecutor);
        }
        PluginCommand artifact = getCommand("artifact");
        if (artifact != null) {
            artifact.setExecutor(adminExecutor);
            artifact.setTabCompleter(adminExecutor);
        }
        boolean museumCommand = towny.register("museum", new TownMuseumCommand(menus, messages));
        getServer().getServicesManager().register(TownyArchaeologyApi.class,
                new ArchaeologyApiService(this, registry, artifacts, museums), this, ServicePriority.Normal);
        boolean papi = PlaceholderHook.register(this, towny, registry, repository, museums);
        startTasks();
        getLogger().info("NeverLandTownyArchaeology " + getPluginMeta().getVersion()
                + " включён: артефактов " + registry.artifacts().size()
                + ", участков " + repository.sites().size() + ", /t archaeology=true, /t museum="
                + museumCommand + ", PlaceholderAPI=" + papi + ".");
    }

    @Override
    public void onDisable() {
        if (sites != null) sites.stop();
        if (autosave != null) autosave.cancel();
        if (repository != null) repository.save();
        if (towny != null) {
            for (String name : USER_TOWN_COMMANDS) towny.unregister(name);
            towny.unregister("museum");
        }
        getServer().getServicesManager().unregisterAll(this);
    }

    public void reloadPlugin() {
        reloadConfig();
        messages.reload();
        itemsAdder.reload();
        registry.reload();
        sites.start();
        startAutosave();
    }

    private void startTasks() {
        sites.start();
        startAutosave();
    }

    private void startAutosave() {
        if (autosave != null) autosave.cancel();
        long ticks = Math.max(20L, getConfig().getLong("storage.autosave-seconds", 60) * 20L);
        autosave = getServer().getScheduler().runTaskTimer(this, repository::save, ticks, ticks);
    }

    private void copy(String name) {
        if (!new File(getDataFolder(), name).exists()) saveResource(name, false);
    }
}
