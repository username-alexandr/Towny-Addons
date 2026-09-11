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

    @Override public void onEnable() {
        ru.neverland.mintcontracts.util.LegacyDataMigrator.migrate(this, "MintTownyContracts");
        saveDefaultConfig();getConfig().options().copyDefaults(true);saveConfig(); copy("messages.yml"); copy("contracts.yml"); copy("item-names.yml");
        towny = new TownyHook(this); itemsAdder = new ItemsAdderHook(this); messages = new MessageService(this); names = new RussianNames(this);
        registry = new ContractRegistry(this, itemsAdder); repository = new ContractRepository(this); repository.load();
        WarehouseBridge warehouse = new WarehouseBridge(this); EconomyService economy = new EconomyService(this, towny);
        contracts = new ContractService(this, towny, registry, repository, warehouse, economy, messages);
        try{contracts.initialize();}catch(Exception ex){getLogger().severe("Контракты отключены: "+ex.getMessage());getServer().getPluginManager().disablePlugin(this);return;}
        ContractMenuManager menus = new ContractMenuManager(this, towny, contracts, messages, names);
        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(new ProgressListener(contracts), this);
        towny.register("contracts", new TownContractsCommand(towny, contracts, menus, messages));
        PluginCommand admin = getCommand("townycontracts");
        if (admin != null) {
            AdminCommand executor = new AdminCommand(this, towny, contracts, messages);
            admin.setExecutor(executor); admin.setTabCompleter(executor);
        }
        getServer().getServicesManager().register(MintTownyContractsApi.class, contracts, this, ServicePriority.Normal);
        boolean placeholders = PlaceholderHook.register(this, towny, contracts);
        contracts.start();
        getLogger().info("NeverLandTownyContracts 0.3.0 включён: шаблонов " + registry.all().size()
                + ", склад=" + warehouse.available() + ", PlaceholderAPI=" + placeholders + ".");
    }

    @Override public void onDisable() {
        if (contracts != null) contracts.shutdown();
        if (towny != null) towny.unregister("contracts");
        getServer().getServicesManager().unregisterAll(this);
    }

    public ContractService companyContracts() { return contracts; }

    public void reloadPlugin() {
        reloadConfig(); messages.reload(); itemsAdder.reload(); names.reload(); registry.reload(); contracts.reloadRuntime();
    }
    private void copy(String name) { if (!new File(getDataFolder(), name).exists()) saveResource(name, false); }
}
