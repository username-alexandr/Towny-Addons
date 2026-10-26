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
    private MenuManager uiMenus;
    /** ConstructionService returns supplied blocks after placement; UI must not quote them as consumed. */
    public boolean uiReturnsPlacedMaterial() { return true; }
    /** Opens a real, guarded details inventory on the caller player entity thread. */
    public void uiOpenDetails(org.bukkit.entity.Player player, String id) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        if (!player.hasPermission("neverlandtownybuilds.use")) throw new IllegalArgumentException("Нет доступа к постройкам");
        var project = definitions.project(id);
        if (project == null) throw new IllegalArgumentException("Постройка больше не существует");
        uiMenus.openDetails(player, project);
    } }
    private DataStore dataStore;
    private EffectService effects;
    private RussianItemNames itemNames;
    private ru.neverland.townybuilds.construction.BlueprintThumbnail thumbnails;
    private ConstructionService construction;
    private ru.neverland.townybuilds.construction.PlacementService placements;
    private CivicService civic;
    private ru.neverland.townybuilds.storage.ProductionService production;
    private ru.neverland.townybuilds.storage.BuildingStorageService storage;
    public ru.neverland.townybuilds.storage.BuildingStorageService storage(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return storage;} }
    private ru.neverland.townybuilds.army.ArmyService army;

    private ru.neverland.townybuilds.construction.ConstructionRefunds constructionRefunds;
    private ru.neverland.townybuilds.construction.ConstructionCancelMenu constructionCancelMenu;
    public ru.neverland.townybuilds.construction.ConstructionRefunds constructionRefunds() { return constructionRefunds; }
    public ru.neverland.townybuilds.construction.ConstructionCancelMenu constructionCancelMenu() { return constructionCancelMenu; }

    @Override
    public void onEnable() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;

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
        new ru.neverland.townybuilds.storage.WarehouseService(this,dataStore);
        BuildService builds = new BuildService(this, towny, dataStore, messages, new ArchaeologyBridge(this), itemNames, definitions);
        construction = new ConstructionService(this, towny, dataStore, messages, itemNames,
                new BuildingBlueprintGenerator(), builds::completeConstruction);
        constructionRefunds = new ru.neverland.townybuilds.construction.ConstructionRefunds(this,dataStore,towny,construction,definitions,new ArchaeologyBridge(this),itemNames);
        construction.refunds(constructionRefunds);
        builds.refunds(constructionRefunds);
        constructionCancelMenu = new ru.neverland.townybuilds.construction.ConstructionCancelMenu(this,constructionRefunds,towny);
        getServer().getPluginManager().registerEvents(constructionCancelMenu,this);
        builds.setConstruction(construction);
        placements=new ru.neverland.townybuilds.construction.PlacementService(this,towny,dataStore,definitions,construction,builds);
        builds.placements(placements);getServer().getPluginManager().registerEvents(placements,this);
        placements.start();
        var placementCommand=getCommand("townybuildplace");if(placementCommand!=null)placementCommand.setExecutor(placements);
        MenuManager menus = new MenuManager(this, definitions, dataStore, towny, itemsAdder, builds, messages, itemNames);
        uiMenus = menus;
        thumbnails=new ru.neverland.townybuilds.construction.BlueprintThumbnail(this);
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
        ru.neverland.core.ActivityAdmin.attach(this,"townybuilds","neverlandtownybuilds.admin",construction::adminTargets);
        construction.start();
        if(placements!=null)placements.start();
        constructionRefunds.start();
        civic.start();
        if(production==null)production=new ru.neverland.townybuilds.storage.ProductionService(this,dataStore,storage);
        production.start();
        long autosave = Math.max(20L, getConfig().getLong("settings.storage.autosave-seconds", 60L) * 20L);
        ru.neverland.core.SuiteScheduler.get().runTaskTimer(this, dataStore::saveIfDirty, autosave, autosave);
        getLogger().info("NeverLandTownyBuilds " + getPluginMeta().getVersion()
                + " включён: процедурные здания и Чудеса, фонды ресурсов и совместное строительство готовы.");
    } }

    @Override
    public void onDisable() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        if (!ru.neverland.core.ModuleLifecycle.end(this)) return;

        if (effects != null) effects.stop();
        if(thumbnails!=null)thumbnails.clear();
        if (placements != null) placements.stop();
        if (construction != null) construction.stop();
        if (constructionRefunds != null) constructionRefunds.stop();
        if (civic != null) civic.stop();
        if (army != null) army.stop();
        if (production != null) production.stop();
        if (storage != null) storage.stop();
        if (dataStore != null) dataStore.save();
        TownyHook towny = new TownyHook();
        for (String command : TOWN_COMMANDS) towny.unregisterTownCommand(command);
    } }

    public java.util.concurrent.CompletableFuture<java.util.Map<String,String>> buildingPreview(org.bukkit.entity.Player player,org.bukkit.inventory.Inventory inventory){try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()){
        if(!isEnabled()||ru.neverland.core.SuiteState.frozen(getName())||uiMenus==null||thumbnails==null)return java.util.concurrent.CompletableFuture.completedFuture(java.util.Map.of());
        var plan=uiMenus.preview(player,inventory);return plan==null?java.util.concurrent.CompletableFuture.completedFuture(java.util.Map.of()):thumbnails.preview(plan);
    }}
    public java.util.Map<String,String> placementControls(org.bukkit.entity.Player player){return placements==null?java.util.Map.of():placements.controls(player);}
    public boolean nudgePlacement(org.bukkit.entity.Player player,String key,long sequence,String action){return placements!=null&&placements.nudge(player,key,sequence,action);}
    public void reloadPlugin() { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
        reloadConfig();
        messages.reload();
        itemsAdder.reload();
        itemNames.reload();
        definitions.reload();
        if(thumbnails!=null)thumbnails.clear();
        effects.start();
        construction.start();
        if(placements!=null)placements.start();
        constructionRefunds.start();
        civic.start();
        if(production==null)production=new ru.neverland.townybuilds.storage.ProductionService(this,dataStore,storage);
        production.start();
    } }

    private void registerTownCommands(TownyHook towny, MenuManager menus) {
        towny.registerTownCommand("builds", new TownSubCommand(TownSubCommand.Target.BUILDINGS, menus, messages));
        towny.registerTownCommand("wonderd", new TownSubCommand(TownSubCommand.Target.WONDERS, menus, messages));
        towny.registerTownCommand("wonders", new TownSubCommand(TownSubCommand.Target.WONDERS, menus, messages));
        towny.registerTownCommand("inv", new TownSubCommand(TownSubCommand.Target.STORAGE, menus, messages));
        towny.registerTownCommand("civic", new CivicCommand(civic, messages, false));
        towny.registerTownCommand("shop", new CivicCommand(civic, messages, true));
    }

    public ru.neverland.townybuilds.army.ArmyService army() { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return army; } }

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
