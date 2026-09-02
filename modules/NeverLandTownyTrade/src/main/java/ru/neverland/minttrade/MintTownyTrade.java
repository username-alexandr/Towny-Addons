package ru.neverland.minttrade;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.minttrade.api.MintTownyTradeApi;
import ru.neverland.minttrade.command.AdminCommand;
import ru.neverland.minttrade.command.TownTradeCommand;
import ru.neverland.minttrade.gui.TradeMenuManager;
import ru.neverland.minttrade.integration.BuildBridge;
import ru.neverland.minttrade.integration.CampsBridge;
import ru.neverland.minttrade.integration.ItemsAdderHook;
import ru.neverland.minttrade.integration.PlaceholderHook;
import ru.neverland.minttrade.integration.TownyHook;
import ru.neverland.minttrade.integration.TaxesBridge;
import ru.neverland.minttrade.integration.WarehouseBridge;
import ru.neverland.minttrade.service.EconomyService;
import ru.neverland.minttrade.service.ExportRegistry;
import ru.neverland.minttrade.service.MessageService;
import ru.neverland.minttrade.service.RoutePlanner;
import ru.neverland.minttrade.service.TradeApiService;
import ru.neverland.minttrade.service.TradeRepository;
import ru.neverland.minttrade.service.TradeService;
import ru.neverland.minttrade.service.VisualCaravanService;

import java.io.File;

public final class MintTownyTrade extends JavaPlugin {
    private TownyHook towny; private ItemsAdderHook itemsAdder; private MessageService messages;
    private ExportRegistry registry; private TradeRepository repository; private TradeService trade;
    private VisualCaravanService visuals;

    @Override public void onEnable() {
        ru.neverland.minttrade.util.LegacyDataMigrator.migrate(this, "MintTownyTrade");
        saveDefaultConfig(); copy("messages.yml"); copy("exports.yml");
        towny = new TownyHook(this); itemsAdder = new ItemsAdderHook(); messages = new MessageService(this);
        registry = new ExportRegistry(this, itemsAdder); repository = new TradeRepository(this); repository.load();
        BuildBridge builds = new BuildBridge(this); WarehouseBridge warehouse = new WarehouseBridge(this);
        CampsBridge camps = new CampsBridge(this, towny); RoutePlanner routes = new RoutePlanner(this, towny, builds, camps);
        EconomyService economy = new EconomyService(this);
        TaxesBridge taxes = new TaxesBridge(this);
        trade = new TradeService(this, towny, builds, warehouse, registry, repository, routes, economy, messages, taxes);
        TradeMenuManager menus = new TradeMenuManager(this, towny, trade, messages);
        getServer().getPluginManager().registerEvents(menus, this);
        if (!towny.register("trade", new TownTradeCommand(towny, trade, menus, messages)))
            getLogger().severe("Не удалось зарегистрировать подкоманду /t trade.");
        PluginCommand admin = getCommand("townytrade");
        if (admin != null) { AdminCommand executor = new AdminCommand(this, towny, trade, messages); admin.setExecutor(executor); admin.setTabCompleter(executor); }
        TradeApiService api = new TradeApiService(towny, trade);
        getServer().getServicesManager().register(MintTownyTradeApi.class, api, this, ServicePriority.Normal);
        boolean placeholders = PlaceholderHook.register(this, towny, trade);
        visuals = new VisualCaravanService(this, repository, towny); trade.start(); visuals.start();
        int campStops = camps.activeStops().size();
        getLogger().info("NeverLandTownyTrade 0.1.3 включён: экспортов " + registry.all().size() + ", склад="
                + warehouse.available() + ", лагеря=" + (getServer().getPluginManager().isPluginEnabled("NeverLandTownyCamps") || getServer().getPluginManager().isPluginEnabled("MintTownyCamps"))
                + ", активных перевалочных пунктов=" + campStops + ", налоги=" + taxes.available() + ", PlaceholderAPI=" + placeholders + ".");
    }
    @Override public void onDisable() {
        if (visuals != null) visuals.stop(); if (trade != null) trade.shutdown();
        if (towny != null) towny.unregister("trade"); getServer().getServicesManager().unregisterAll(this);
    }
    public void reloadPlugin() {
        reloadConfig(); messages.reload(); itemsAdder.reload(); registry.reload(); trade.start(); visuals.start();
    }
    private void copy(String name) { if (!new File(getDataFolder(), name).exists()) saveResource(name, false); }
}
