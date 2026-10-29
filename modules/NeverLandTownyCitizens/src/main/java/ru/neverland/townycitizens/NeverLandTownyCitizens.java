package ru.neverland.townycitizens;

import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townycitizens.api.TownyCitizensApi;

public final class NeverLandTownyCitizens extends JavaPlugin {
    private CitizensService service;
    private AdmissionsController admissions;
    private boolean townCommand;
    private Runnable unregisterPlaceholders;
    @Override public void onEnable() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;

        try {
            saveDefaultConfig(); var repository = new CitizensRepository(getDataFolder().toPath().resolve("citizens.yml")); repository.load();
            service = new CitizensService(repository, readSettings(),this);
            ru.neverland.core.ModuleShutdown.register(this,service::prepareClose);
            admissions=new AdmissionsController(this,service,new AdmissionsService(this,service));
            var menus = new CitizensMenus(this, service);menus.admissions(admissions);
            var command = new CitizensCommand(this, service, menus,admissions);
            getCommand("townycitizens").setExecutor(command); getCommand("townycitizens").setTabCompleter(command);
            townCommand = TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "citizens", command);
            if (!townCommand) getLogger().warning("/t citizens занят; используйте /townycitizens");
            getServer().getPluginManager().registerEvents(menus, this);
            getServer().getPluginManager().registerEvents(admissions,this);
            getServer().getPluginManager().registerEvents(new CitizensListener(service), this);
            getServer().getServicesManager().register(TownyCitizensApi.class, service, this, ServicePriority.Normal);
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) unregisterPlaceholders = CitizensPlaceholders.register(this, service);
            getLogger().info("Реестр гражданства загружен. Четыре статуса; /t citizens.");
        } catch (Exception | LinkageError ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Гражданство не загружено; исходный реестр сохранён", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    } }
    private CitizensSettings readSettings() throws Exception {
        var yaml = new YamlConfiguration(); yaml.load(getDataFolder().toPath().resolve("config.yml").toFile()); return CitizensSettings.load(yaml);
    }
    public void reloadSettings() throws Exception { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { var next = readSettings(); service.settings(next); reloadConfig(); } }
    public CitizensService citizens() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return service; } }
    @Override public void onDisable() { try { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        if (!ru.neverland.core.ModuleLifecycle.end(this)) return;

        if(service!=null)service.prepareClose();if(admissions!=null)admissions.close();
        if (unregisterPlaceholders != null) unregisterPlaceholders.run();
        getServer().getServicesManager().unregisterAll(this);
        if (townCommand) TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "citizens");
        ru.neverland.core.SuiteScheduler.cleanupPlayers(this,p->{if (p.getOpenInventory().getTopInventory().getHolder() != null
                && p.getOpenInventory().getTopInventory().getHolder().getClass().getName().startsWith("ru.neverland.townycitizens.")) p.closeInventory();});
    } } finally {if(service!=null)service.awaitClosed();ru.neverland.core.ModuleShutdown.remove(this);} }
}
