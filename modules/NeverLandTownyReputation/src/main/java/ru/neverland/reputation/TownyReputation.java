package ru.neverland.reputation;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.reputation.api.TownyReputationApi;
import ru.neverland.reputation.command.AdminCommand;
import ru.neverland.reputation.command.ReputationCommand;
import ru.neverland.reputation.gui.ReputationMenuManager;
import ru.neverland.reputation.integration.ItemsAdderHook;
import ru.neverland.reputation.integration.PlaceholderHook;
import ru.neverland.reputation.integration.TownyHook;
import ru.neverland.reputation.model.ReputationScope;
import ru.neverland.reputation.service.MessageService;
import ru.neverland.reputation.service.ReputationApiService;
import ru.neverland.reputation.service.ReputationRegistry;
import ru.neverland.reputation.service.ReputationRepository;
import ru.neverland.reputation.service.ReputationService;

import java.io.File;

public final class TownyReputation extends JavaPlugin {
    private TownyHook towny; private ItemsAdderHook itemsAdder; private MessageService messages; private ReputationRegistry registry; private ReputationRepository repository; private ReputationService reputation;
    @Override public void onEnable() {
        ru.neverland.reputation.util.LegacyDataMigrator.migrate(this, "TownyReputation");
        saveDefaultConfig(); copy("messages.yml"); copy("levels.yml");
        towny = new TownyHook(); itemsAdder = new ItemsAdderHook(); messages = new MessageService(this); registry = new ReputationRegistry(this); repository = new ReputationRepository(this); repository.load(); reputation = new ReputationService(this, registry, repository);
        ReputationMenuManager menus = new ReputationMenuManager(this, towny, reputation, itemsAdder, messages); getServer().getPluginManager().registerEvents(menus, this);
        ReputationCommand direct = new ReputationCommand(this, null, towny, reputation, menus, messages); PluginCommand user = getCommand("reputation"); if (user != null) { user.setExecutor(direct); user.setTabCompleter(direct); }
        AdminCommand adminExecutor = new AdminCommand(this, towny, reputation, messages); PluginCommand admin = getCommand("townyreputation"); if (admin != null) { admin.setExecutor(adminExecutor); admin.setTabCompleter(adminExecutor); }
        boolean townCommand = towny.registerTown("reputation", new ReputationCommand(this, ReputationScope.TOWN, towny, reputation, menus, messages)); boolean nationCommand = towny.registerNation("reputation", new ReputationCommand(this, ReputationScope.NATION, towny, reputation, menus, messages));
        getServer().getServicesManager().register(TownyReputationApi.class, new ReputationApiService(this, reputation), this, ServicePriority.Normal); boolean placeholders = PlaceholderHook.register(this, towny, reputation); reputation.start();
        getLogger().info("NeverLandTownyReputation 0.1.2 включён: связей " + repository.all().size() + ", уровней " + registry.tiers().size() + ", /t=" + townCommand + ", /n=" + nationCommand + ", PlaceholderAPI=" + placeholders + ".");
    }
    @Override public void onDisable() { if (reputation != null) reputation.shutdown(); if (towny != null) { towny.unregisterTown("reputation"); towny.unregisterNation("reputation"); } getServer().getServicesManager().unregisterAll(this); }
    public void reloadPlugin() { reloadConfig(); messages.reload(); itemsAdder.reload(); registry.reload(); reputation.start(); }
    private void copy(String name) { if (!new File(getDataFolder(), name).exists()) saveResource(name, false); }
}
