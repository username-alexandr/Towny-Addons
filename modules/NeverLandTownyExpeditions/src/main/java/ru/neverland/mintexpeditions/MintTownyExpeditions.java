package ru.neverland.mintexpeditions;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintexpeditions.command.AdminCommand;
import ru.neverland.mintexpeditions.command.ExpeditionCommand;
import ru.neverland.mintexpeditions.gui.ExpeditionMenuManager;
import ru.neverland.mintexpeditions.integration.CampFacade;
import ru.neverland.mintexpeditions.integration.ItemsAdderHook;
import ru.neverland.mintexpeditions.integration.TownyHook;
import ru.neverland.mintexpeditions.listener.ExpeditionListener;
import ru.neverland.mintexpeditions.service.ExpeditionRegistry;
import ru.neverland.mintexpeditions.service.ExpeditionRepository;
import ru.neverland.mintexpeditions.service.ExpeditionService;
import ru.neverland.mintexpeditions.service.MessageService;
import ru.neverland.mintexpeditions.service.RussianItemNames;
import ru.neverland.mintexpeditions.service.SiteService;

import java.io.File;
import java.util.List;

public final class MintTownyExpeditions extends JavaPlugin {
    private static final List<String> TOWN_COMMANDS = List.of("expeditions", "expedition");

    private MessageService messages;
    private ItemsAdderHook itemsAdder;
    private RussianItemNames itemNames;
    private ExpeditionRegistry registry;
    private ExpeditionRepository repository;
    private ExpeditionService service;
    private TownyHook towny;

    @Override
    public void onEnable() {
        ru.neverland.mintexpeditions.util.LegacyDataMigrator.migrate(this, "MintTownyExpeditions");
        saveDefaultConfig();
        copy("messages.yml");
        copy("expeditions.yml");
        copy("item-names.yml");
        messages = new MessageService(this);
        itemsAdder = new ItemsAdderHook();
        itemNames = new RussianItemNames(this);
        registry = new ExpeditionRegistry(this, itemsAdder);
        repository = new ExpeditionRepository(this);
        repository.load();
        towny = new TownyHook();
        CampFacade camps = new CampFacade(this, itemNames);
        SiteService sites = new SiteService(this, towny, repository);
        for (var expedition : repository.active()) {
            var definition = registry.get(expedition.definitionId());
            if (definition != null) sites.resume(expedition, definition);
        }
        service = new ExpeditionService(this, messages, registry, repository, camps, sites, towny);
        ExpeditionMenuManager menu = new ExpeditionMenuManager(this, service, itemNames);
        Bukkit.getPluginManager().registerEvents(menu, this);
        Bukkit.getPluginManager().registerEvents(new ExpeditionListener(service), this);
        ExpeditionCommand command = new ExpeditionCommand(service, menu, messages);
        bind("expedition", command, command);
        for (String name : TOWN_COMMANDS) {
            if (!towny.register(name, command)) {
                getLogger().warning("Не удалось зарегистрировать /t " + name + "; используйте /expedition.");
            }
        }
        AdminCommand admin = new AdminCommand(this);
        bind("townyexpeditions", admin, admin);
        service.startTasks();
        getLogger().info("NeverLandTownyExpeditions 0.1.4 включён: доступно "
                + registry.all().size() + " экспедиций, команды /t expeditions готовы.");
    }

    @Override
    public void onDisable() {
        if (service != null) service.stopTasks();
        if (repository != null) repository.save();
        if (towny != null) for (String name : TOWN_COMMANDS) towny.unregister(name);
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
        itemsAdder.reload();
        itemNames.reload();
        registry.reload();
        service.startTasks();
    }

    private void copy(String name) {
        if (!new File(getDataFolder(), name).exists()) saveResource(name, false);
    }

    private void bind(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) throw new IllegalStateException(name);
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    public MessageService messages() { return messages; }
    public ExpeditionService service() { return service; }
}
