package ru.neverland.townyideologies;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townyideologies.command.AdminCommand;
import ru.neverland.townyideologies.command.TownIdeologyCommand;
import ru.neverland.townyideologies.data.DataStore;
import ru.neverland.townyideologies.gui.MenuManager;
import ru.neverland.townyideologies.integration.ItemsAdderHook;
import ru.neverland.townyideologies.integration.TownyHook;
import ru.neverland.townyideologies.placeholder.IdeologyExpansion;
import ru.neverland.townyideologies.service.BonusService;
import ru.neverland.townyideologies.service.EconomyService;
import ru.neverland.townyideologies.service.IdeologyRegistry;
import ru.neverland.townyideologies.service.IdeologyService;
import ru.neverland.townyideologies.service.MessageService;

import java.io.File;

public final class NeverLandTownyIdeologies extends JavaPlugin {
    private static final String[] TOWN_COMMANDS = {"ideology", "ideologies"};

    private TownyHook towny;
    private ItemsAdderHook itemsAdder;
    private MessageService messages;
    private IdeologyRegistry registry;
    private DataStore data;
    private IdeologyService ideologies;
    private BonusService bonuses;
    private IdeologyExpansion placeholders;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        copyResource("messages.yml");
        copyResource("ideologies.yml");

        towny = new TownyHook();
        itemsAdder = new ItemsAdderHook(this);
        messages = new MessageService(this);
        registry = new IdeologyRegistry(this);
        data = new DataStore(this);
        data.load();
        EconomyService economy = new EconomyService(this, itemsAdder);
        ideologies = new IdeologyService(this, towny, data, registry, economy);
        MenuManager menus = new MenuManager(this, towny, registry, ideologies, economy, itemsAdder, messages);
        bonuses = new BonusService(this, towny, data, ideologies, registry);

        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(bonuses, this);
        TownIdeologyCommand townCommand = new TownIdeologyCommand(menus, messages);
        for (String name : TOWN_COMMANDS) towny.registerTownCommand(name, townCommand);

        PluginCommand admin = getCommand("townyideologies");
        if (admin != null) {
            AdminCommand executor = new AdminCommand(this, towny, registry, ideologies, messages);
            admin.setExecutor(executor);
            admin.setTabCompleter(executor);
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                placeholders = new IdeologyExpansion(this, ideologies, registry);
                placeholders.register();
            } catch (LinkageError error) {
                placeholders = null;
                getLogger().warning("PlaceholderAPI несовместим: " + error.getMessage());
            }
        }
        bonuses.start();
        long autosave = Math.max(20L, getConfig().getLong("settings.data.autosave-seconds", 60L) * 20L);
        Bukkit.getScheduler().runTaskTimer(this, data::saveIfDirty, autosave, autosave);
        getLogger().info("NeverLandTownyIdeologies " + getPluginMeta().getVersion()
                + " включён: 6 идеологий, Paper 26.2, Towny и ItemsAdder-ready.");
    }

    @Override
    public void onDisable() {
        if (bonuses != null) bonuses.stop();
        if (data != null) data.save();
        if (placeholders != null) placeholders.unregister();
        if (towny != null) for (String name : TOWN_COMMANDS) towny.unregisterTownCommand(name);
    }

    public void reloadPlugin() {
        reloadConfig();
        messages.reload();
        itemsAdder.reload();
        registry.reload();
        bonuses.start();
    }

    public IdeologyService ideologyService() {
        return ideologies;
    }

    public IdeologyRegistry ideologyRegistry() {
        return registry;
    }

    private void copyResource(String name) {
        if (!new File(getDataFolder(), name).exists()) saveResource(name, false);
    }
}
