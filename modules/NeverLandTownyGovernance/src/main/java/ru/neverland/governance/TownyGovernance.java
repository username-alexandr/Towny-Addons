package ru.neverland.governance;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.governance.api.TownyGovernanceApi;
import ru.neverland.governance.command.AdminCommand;
import ru.neverland.governance.command.TownGovernanceCommand;
import ru.neverland.governance.gui.GovernanceMenuManager;
import ru.neverland.governance.integration.ItemsAdderHook;
import ru.neverland.governance.integration.PlaceholderHook;
import ru.neverland.governance.integration.TownyHook;
import ru.neverland.governance.service.DefinitionRegistry;
import ru.neverland.governance.service.GovernanceApiService;
import ru.neverland.governance.service.GovernanceRepository;
import ru.neverland.governance.service.GovernanceService;
import ru.neverland.governance.service.MessageService;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TownyGovernance extends JavaPlugin {
    private final Map<String, TownGovernanceCommand.DefaultView> townCommands = new LinkedHashMap<>();
    private TownyHook towny; private ItemsAdderHook itemsAdder; private MessageService messages; private DefinitionRegistry definitions;
    private GovernanceRepository repository; private GovernanceService governance;

    @Override public void onEnable() {
        ru.neverland.governance.util.LegacyDataMigrator.migrate(this, "TownyGovernance");
        saveDefaultConfig(); copy("messages.yml"); copy("laws.yml"); copy("offices.yml");
        towny = new TownyHook(this); itemsAdder = new ItemsAdderHook(); messages = new MessageService(this); definitions = new DefinitionRegistry(this);
        repository = new GovernanceRepository(this); repository.load(); governance = new GovernanceService(this, towny, definitions, repository, messages);
        GovernanceMenuManager menus = new GovernanceMenuManager(this, towny, governance, itemsAdder, messages);
        getServer().getPluginManager().registerEvents(menus, this);
        TownGovernanceCommand direct = new TownGovernanceCommand(TownGovernanceCommand.DefaultView.MAIN, towny, governance, menus, messages);
        PluginCommand command = getCommand("governance"); if (command != null) { command.setExecutor(direct); command.setTabCompleter(direct); }
        PluginCommand admin = getCommand("townygovernance"); if (admin != null) { AdminCommand executor = new AdminCommand(this, towny, governance, messages); admin.setExecutor(executor); admin.setTabCompleter(executor); }
        registerTownCommands(menus);
        TownyGovernanceApi api = new GovernanceApiService(towny, governance);
        getServer().getServicesManager().register(TownyGovernanceApi.class, api, this, ServicePriority.Normal);
        boolean placeholders = PlaceholderHook.register(this, towny, governance); governance.start();
        getLogger().info("NeverLandTownyGovernance " + getPluginMeta().getVersion() + " включён: законов " + definitions.laws().size() + ", должностей " + definitions.offices().size()
                + ", открытых голосований " + repository.open().size() + ", PlaceholderAPI=" + placeholders + ".");
    }

    @Override public void onDisable() {
        if (governance != null) governance.shutdown(); if (towny != null) townCommands.keySet().forEach(towny::unregister);
        getServer().getServicesManager().unregisterAll(this);
    }

    public void reloadPlugin() { reloadConfig(); messages.reload(); itemsAdder.reload(); definitions.reload(); governance.start(); }

    private void registerTownCommands(GovernanceMenuManager menus) {
        townCommands.put("governance", TownGovernanceCommand.DefaultView.MAIN); townCommands.put("laws", TownGovernanceCommand.DefaultView.LAWS);
        townCommands.put("vote", TownGovernanceCommand.DefaultView.VOTES); townCommands.put("council", TownGovernanceCommand.DefaultView.COUNCIL);
        for (Map.Entry<String, TownGovernanceCommand.DefaultView> entry : townCommands.entrySet()) {
            boolean registered = towny.register(entry.getKey(), new TownGovernanceCommand(entry.getValue(), towny, governance, menus, messages));
            if (!registered) getLogger().warning("Не удалось зарегистрировать /t " + entry.getKey() + "; используйте /governance.");
        }
    }
    private void copy(String name) { if (!new File(getDataFolder(), name).exists()) saveResource(name, false); }
}
