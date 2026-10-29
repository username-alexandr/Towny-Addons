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
    private ru.neverland.townybuilds.construction.CityStyles cityStyles;
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
    private ru.neverland.townybuilds.payments.BuildPayments payments;
    public ru.neverland.townybuilds.payments.BuildPayments payments(){return payments;}
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
        ru.neverland.core.ModuleShutdown.register(this,this::prepareShutdown);
        dataStore.load();
        try{ru.neverland.townybuilds.construction.BlueprintCatalog.initialize(this,dataStore);}
        catch(java.io.IOException error){throw new IllegalStateException("Не удалось загрузить закреплённые чертежи; городские данные сохранены",error);}
        storage = new ru.neverland.townybuilds.storage.BuildingStorageService(this,dataStore,definitions);
        storage.start();
        new ru.neverland.townybuilds.storage.MarketStorageService(this,dataStore);
        new ru.neverland.townybuilds.storage.MunicipalStorageService(this,dataStore);
        new ru.neverland.townybuilds.storage.WarehouseService(this,dataStore);
        new ru.neverland.townybuilds.storage.RaidStorageService(this,dataStore,storage);
        new ru.neverland.townybuilds.construction.BuildingRepairPlans(this,dataStore,definitions);
        new ru.neverland.townybuilds.service.BuildingMaterials(this,dataStore,definitions,itemNames);
        new ru.neverland.townybuilds.planning.BuildingPlanning(this,dataStore,definitions,itemNames);
        cityStyles=new ru.neverland.townybuilds.construction.CityStyles(this,dataStore,towny);
        BuildService builds = new BuildService(this, towny, dataStore, messages, new ArchaeologyBridge(this), itemNames, definitions);
        construction = new ConstructionService(this, towny, dataStore, messages, itemNames,
                new BuildingBlueprintGenerator(), builds::completeConstruction);
        constructionRefunds = new ru.neverland.townybuilds.construction.ConstructionRefunds(this,dataStore,towny,construction,definitions,new ArchaeologyBridge(this),itemNames);
        construction.refunds(constructionRefunds);
        builds.refunds(constructionRefunds);
        payments=new ru.neverland.townybuilds.payments.BuildPayments(this,dataStore,towny,construction,new ArchaeologyBridge(this));
        builds.payments(payments);constructionRefunds.payments(payments);
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
        civic = new CivicService(this, towny, dataStore, messages, itemNames);civic.payments(payments);

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
        constructionRefunds.start();payments.start();
        civic.start();
        if(production==null)production=new ru.neverland.townybuilds.storage.ProductionService(this,dataStore,storage);
        production.start();
        long autosave = Math.max(20L, getConfig().getLong("settings.storage.autosave-seconds", 60L) * 20L);
        ru.neverland.core.SuiteScheduler.get().runTaskTimer(this, dataStore::saveIfDirty, autosave, autosave);
        getLogger().info("NeverLandTownyBuilds " + getPluginMeta().getVersion()
                + " включён: процедурные здания и Чудеса, фонды ресурсов и совместное строительство готовы.");
    } }

    private java.util.concurrent.CompletionStage<Void> shutdown;
    private java.util.concurrent.CompletionStage<Void> prepareShutdown(){try(var guard=ru.neverland.core.SuiteState.guard()){
        if(shutdown!=null)return shutdown;
        if(storage!=null)storage.stop();
        var purchases=payments==null?java.util.concurrent.CompletableFuture.<Void>completedFuture(null):payments.prepareClose();
        var returns=constructionRefunds==null?java.util.concurrent.CompletableFuture.<Void>completedFuture(null):constructionRefunds.prepareClose();
        shutdown=java.util.concurrent.CompletableFuture.allOf(purchases.toCompletableFuture(),returns.toCompletableFuture()).thenCompose(v->dataStore==null?java.util.concurrent.CompletableFuture.completedFuture(null):dataStore.prepareClose());
        return shutdown;
    }}
    @Override
    public void onDisable() {
        java.util.concurrent.CompletionStage<Void> closing=java.util.concurrent.CompletableFuture.completedFuture(null);
        try(var guard=ru.neverland.core.SuiteState.guard()){
            if(!ru.neverland.core.ModuleLifecycle.end(this))return;
            closing=prepareShutdown();
        if(cityStyles!=null)cityStyles.close();
        if (effects != null) effects.stop();
        if(thumbnails!=null)thumbnails.clear();
        if (placements != null) placements.stop();
        if (construction != null) construction.stop();
        if (constructionRefunds != null) constructionRefunds.stop();
        if (civic != null) civic.stop();
        if (army != null) army.stop();
        if (production != null) production.stop();
        if (storage != null) storage.stop();
        getServer().getServicesManager().unregisterAll(this);
        TownyHook towny = new TownyHook();
        for (String command : TOWN_COMMANDS) towny.unregisterTownCommand(command);
        }
        var receipt=closing.toCompletableFuture();
        if(ru.neverland.core.SuiteState.held()&&!receipt.isDone()){
            receipt.whenCompleteAsync((v,error)->{if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"Не подтверждено завершение записи построек; данные требуют сверки",error);});return;
        }
        try{receipt.get(10,java.util.concurrent.TimeUnit.SECONDS);}
        catch(InterruptedException error){Thread.currentThread().interrupt();getLogger().log(java.util.logging.Level.SEVERE,"Остановка записи построек прервана",error);}
        catch(Exception error){getLogger().log(java.util.logging.Level.SEVERE,"Не подтверждено завершение записи построек; данные требуют сверки",error);}
    }

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
        constructionRefunds.start();payments.start();
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
