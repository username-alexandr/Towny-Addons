package ru.neverland.townybuilds;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townybuilds.command.AdminCommand;
import ru.neverland.townybuilds.command.EditorCommand;
import ru.neverland.townybuilds.command.TownSubCommand;
import ru.neverland.townybuilds.command.CivicCommand;
import ru.neverland.townybuilds.civic.CivicService;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.gui.EditorManager;
import ru.neverland.townybuilds.gui.MenuManager;
import ru.neverland.townybuilds.integration.ItemsAdderHook;
import ru.neverland.townybuilds.integration.ArchaeologyBridge;
import ru.neverland.townybuilds.integration.TownyHook;
import ru.neverland.townybuilds.service.BuildService;
import ru.neverland.townybuilds.service.DefinitionRegistry;
import ru.neverland.townybuilds.service.EffectService;
import ru.neverland.townybuilds.service.MessageService;
import ru.neverland.townybuilds.service.RussianItemNames;
import ru.neverland.townybuilds.construction.BuildingBlueprintGenerator;
import ru.neverland.townybuilds.construction.ConstructionService;

import java.io.File;

public final class NeverLandTownyBuilds extends JavaPlugin {
    private static final String[] TOWN_COMMANDS = {"builds", "wonderd", "wonders", "inv", "civic", "shop", "army"};
    private MessageService messages;
    private ItemsAdderHook itemsAdder;
    private DefinitionRegistry definitions;
    private DataStore dataStore;
    private EffectService effects;
    private RussianItemNames itemNames;
    private ConstructionService construction;
    private CivicService civic;
    private ru.neverland.townybuilds.storage.ProductionService production;
    private ru.neverland.townybuilds.storage.BuildingStorageService storage;
    public ru.neverland.townybuilds.storage.BuildingStorageService storage(){return storage;}
    private ru.neverland.townybuilds.army.ArmyService army;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        copyResource("messages.yml");
        copyResource("projects.yml");
        copyResource("item-names.yml");
        copyResource("production.yml");

        messages = new MessageService(this);
        TownyHook towny = new TownyHook();
        itemsAdder = new ItemsAdderHook(this);
        itemNames = new RussianItemNames(this);
        definitions = new DefinitionRegistry(this, itemsAdder);
        int storageSize = normalizedStorageSize(getConfig().getInt("settings.storage.size", 54));
        dataStore = new DataStore(this, storageSize);
        dataStore.load();
        storage = new ru.neverland.townybuilds.storage.BuildingStorageService(this,dataStore,definitions);
        storage.start();
        new ru.neverland.townybuilds.storage.MarketStorageService(this,dataStore);
        new ru.neverland.townybuilds.storage.MunicipalStorageService(this,dataStore);
        BuildService builds = new BuildService(this, towny, dataStore, messages, new ArchaeologyBridge(this), itemNames, definitions);
        construction = new ConstructionService(this, towny, dataStore, messages, itemNames,
                new BuildingBlueprintGenerator(), builds::completeConstruction);
        builds.setConstruction(construction);
        MenuManager menus = new MenuManager(this, definitions, dataStore, towny, itemsAdder, builds, messages, itemNames);
        EditorManager editor = new EditorManager(this, definitions, messages);
        civic = new CivicService(this, towny, dataStore, messages, itemNames);

        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(editor, this);
        getServer().getPluginManager().registerEvents(construction, this);
        getServer().getPluginManager().registerEvents(civic, this);

        army = new ru.neverland.townybuilds.army.ArmyService(this, towny, dataStore);
        getServer().getPluginManager().registerEvents(army, this);
        towny.registerTownCommand("army", army);
        PluginCommand armyCommand = getCommand("townyarmy");
        if (armyCommand != null) { armyCommand.setExecutor(army); armyCommand.setTabCompleter(army); }
        army.start();
        registerTownCommands(towny, menus);
        PluginCommand editorCommand = getCommand("buildeditor");
        if (editorCommand != null) editorCommand.setExecutor(new EditorCommand(editor, messages));
        PluginCommand adminCommand = getCommand("townybuilds");
        if (adminCommand != null) {
            AdminCommand executor = new AdminCommand(this, messages, civic);
            adminCommand.setExecutor(executor);
            adminCommand.setTabCompleter(executor);
        }

        effects = new EffectService(this, towny, dataStore, definitions);
        effects.start();
        construction.start();
        civic.start();
        if(production==null)production=new ru.neverland.townybuilds.storage.ProductionService(this,dataStore,storage);
        production.start();
        long autosave = Math.max(20L, getConfig().getLong("settings.storage.autosave-seconds", 60L) * 20L);
        getServer().getScheduler().runTaskTimer(this, dataStore::saveIfDirty, autosave, autosave);
        getLogger().info("NeverLandTownyBuilds " + getPluginMeta().getVersion()
                + " включён: процедурные здания и Чудеса, фонды ресурсов и совместное строительство готовы.");
    }

    @Override
    public void onDisable() {
        if (effects != null) effects.stop();
        if (construction != null) construction.stop();
        if (civic != null) civic.stop();
        if (army != null) army.stop();
        if (production != null) production.stop();
        if (storage != null) storage.stop();
        if (dataStore != null) dataStore.save();
        TownyHook towny = new TownyHook();
        for (String command : TOWN_COMMANDS) towny.unregisterTownCommand(command);
    }

    public void reloadPlugin() {
        reloadConfig();
        messages.reload();
        itemsAdder.reload();
        itemNames.reload();
        definitions.reload();
        effects.start();
        construction.start();
        civic.start();
        if(production==null)production=new ru.neverland.townybuilds.storage.ProductionService(this,dataStore,storage);
        production.start();
    }

    private void registerTownCommands(TownyHook towny, MenuManager menus) {
        towny.registerTownCommand("builds", new TownSubCommand(TownSubCommand.Target.BUILDINGS, menus, messages));
        towny.registerTownCommand("wonderd", new TownSubCommand(TownSubCommand.Target.WONDERS, menus, messages));
        towny.registerTownCommand("wonders", new TownSubCommand(TownSubCommand.Target.WONDERS, menus, messages));
        towny.registerTownCommand("inv", new TownSubCommand(TownSubCommand.Target.STORAGE, menus, messages));
        towny.registerTownCommand("civic", new CivicCommand(civic, messages, false));
        towny.registerTownCommand("shop", new CivicCommand(civic, messages, true));
    }

    public ru.neverland.townybuilds.army.ArmyService army() { return army; }

    private void copyResource(String name) {
        if (!new File(getDataFolder(), name).exists()) saveResource(name, false);
    }

    private int normalizedStorageSize(int configured) {
        int rows = Math.max(1, Math.min(6, (configured + 8) / 9));
        int normalized = rows * 9;
        if (normalized != configured) {
            getLogger().warning("Размер склада " + configured + " скорректирован до " + normalized + " слотов.");
        }
        return normalized;
    }
}
