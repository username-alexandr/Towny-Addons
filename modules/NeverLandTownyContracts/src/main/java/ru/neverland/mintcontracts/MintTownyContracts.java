package ru.neverland.mintcontracts;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcontracts.api.MintTownyContractsApi;
import ru.neverland.mintcontracts.command.AdminCommand;
import ru.neverland.mintcontracts.command.TownContractsCommand;
import ru.neverland.mintcontracts.gui.ContractMenuManager;
import ru.neverland.mintcontracts.integration.ItemsAdderHook;
import ru.neverland.mintcontracts.integration.PlaceholderHook;
import ru.neverland.mintcontracts.integration.TownyHook;
import ru.neverland.mintcontracts.integration.WarehouseBridge;
import ru.neverland.mintcontracts.listener.ProgressListener;
import ru.neverland.mintcontracts.service.ContractRegistry;
import ru.neverland.mintcontracts.service.ContractRepository;
import ru.neverland.mintcontracts.service.ContractService;
import ru.neverland.mintcontracts.service.EconomyService;
import ru.neverland.mintcontracts.service.MessageService;
import ru.neverland.mintcontracts.service.RussianNames;

import java.io.File;

public final class MintTownyContracts extends JavaPlugin {
    private TownyHook towny;
    private ItemsAdderHook itemsAdder;
    private MessageService messages;
    private RussianNames names;
    private ContractRegistry registry;
    private ContractRepository repository;
    private ContractService contracts;
    private ru.neverland.mintcontracts.orders.PublicOrders publicOrders;
    private ru.neverland.mintcontracts.fishing.FishingProtection fishing;
    public ru.neverland.mintcontracts.fishing.FishingProtection fishing(){return fishing;}
    public boolean fishingReady(){return fishing!=null&&fishing.ready();}

    @Override public void onEnable() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;

        ru.neverland.mintcontracts.util.LegacyDataMigrator.migrate(this, "MintTownyContracts");
        saveDefaultConfig();getConfig().options().copyDefaults(true);saveConfig(); copy("messages.yml"); copy("contracts.yml"); copy("item-names.yml");
        towny = new TownyHook(this); itemsAdder = new ItemsAdderHook(this); messages = new MessageService(this); names = new RussianNames(this);
        registry = new ContractRegistry(this, itemsAdder); repository = new ContractRepository(this); repository.load();
        WarehouseBridge warehouse = new WarehouseBridge(this); EconomyService economy = new EconomyService(this, towny);
        contracts = new ContractService(this, towny, registry, repository, warehouse, economy, messages);
        try{contracts.initialize();}catch(Exception ex){getLogger().severe("Контракты отключены: "+ex.getMessage());getServer().getPluginManager().disablePlugin(this);return;}
        ContractMenuManager menus = new ContractMenuManager(this, towny, contracts, messages, names);
        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(new ProgressListener(this,contracts), this);
        towny.register("contracts", new TownContractsCommand(towny, contracts, menus, messages));
        PluginCommand admin = getCommand("townycontracts");
        if (admin != null) {
            AdminCommand executor = new AdminCommand(this, towny, contracts, messages);
            admin.setExecutor(executor); admin.setTabCompleter(executor);
        }
        getServer().getServicesManager().register(MintTownyContractsApi.class, contracts, this, ServicePriority.Normal);
        boolean placeholders = PlaceholderHook.register(this, towny, contracts);
ru.neverland.core.ActivityAdmin.attach(this,"townycontracts","mintcontracts.admin",contracts::adminTargets);
        getCommand("orders").setExecutor((sender,cmd,label,args)->{sender.sendMessage("Раздел заработка приостановлен: проверьте конфигурацию и журнал запуска.");return true;});try { publicOrders=new ru.neverland.mintcontracts.orders.PublicOrders(this,names); } catch(Exception ex) { getLogger().log(java.util.logging.Level.SEVERE,"Публичные заказы приостановлены; городские контракты продолжают работать",ex); }
        new ru.neverland.mintcontracts.service.BuildOrders(this,towny,contracts);
        try{fishing=new ru.neverland.mintcontracts.fishing.FishingProtection(this,id->contracts.hasFishingTask(id)||(publicOrders!=null&&publicOrders.hasFishingTask(id)));}
        catch(Exception ex){getLogger().log(java.util.logging.Level.SEVERE,"Рыбалка заданий приостановлена; остальные контракты работают",ex);}
        ru.neverland.core.ModuleShutdown.register(this,this::prepareShutdown);
        contracts.start();
        getLogger().info("NeverLandTownyContracts "+getPluginMeta().getVersion()+" включён: шаблонов " + registry.all().size()
                + ", склад=" + warehouse.available() + ", PlaceholderAPI=" + placeholders + ".");
    } }

    private java.util.concurrent.CompletionStage<Void> prepareShutdown(){
        var orders=publicOrders==null?java.util.concurrent.CompletableFuture.<Void>completedFuture(null):publicOrders.shutdownAsync();
        var fish=fishing==null?java.util.concurrent.CompletableFuture.<Void>completedFuture(null):fishing.shutdownAsync();
        return java.util.concurrent.CompletableFuture.allOf(orders.toCompletableFuture(),fish.toCompletableFuture());
    }
    @Override public void onDisable(){
        try(var guard=ru.neverland.core.SuiteState.guard()){
            if(!ru.neverland.core.ModuleLifecycle.end(this))return;
            prepareShutdown();if(contracts!=null)contracts.shutdown();
            if(towny!=null){towny.unregister("contracts");towny.unregister("buildorders");}
            getServer().getServicesManager().unregisterAll(this);
        }
        // Control has already awaited this receipt before disabling. Server shutdown has no outer guard.
        try{prepareShutdown().toCompletableFuture().get(5,java.util.concurrent.TimeUnit.SECONDS);}
        catch(InterruptedException error){Thread.currentThread().interrupt();getLogger().log(java.util.logging.Level.SEVERE,"Остановка хранилищ контрактов прервана",error);}
        catch(Exception error){getLogger().log(java.util.logging.Level.SEVERE,"Не подтверждена остановка хранилищ контрактов; проверьте базы перед запуском",error);}
    }

    public ContractService companyContracts() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return contracts; } }

    public void reloadPlugin() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        reloadConfig(); messages.reload(); itemsAdder.reload(); names.reload(); registry.reload(); contracts.reloadRuntime();
        if(fishing!=null)try{fishing.reload();}catch(Exception ex){getLogger().log(java.util.logging.Level.SEVERE,"Настройки рыбалки не применены; прежние правила сохранены",ex);}
    } }
    private void copy(String name) { if (!new File(getDataFolder(), name).exists()) saveResource(name, false); }
}
