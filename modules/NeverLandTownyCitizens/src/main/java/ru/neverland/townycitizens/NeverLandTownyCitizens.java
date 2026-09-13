package ru.neverland.townycitizens;

import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townycitizens.api.TownyCitizensApi;

public final class NeverLandTownyCitizens extends JavaPlugin {
    private CitizensService service;
    private boolean townCommand;
    private Runnable unregisterPlaceholders;
    @Override public void onEnable() {
        try {
            saveDefaultConfig(); var repository = new CitizensRepository(getDataFolder().toPath().resolve("citizens.yml")); repository.load();
            service = new CitizensService(repository, readSettings()); var menus = new CitizensMenus(this, service);
            var command = new CitizensCommand(this, service, menus);
            getCommand("townycitizens").setExecutor(command); getCommand("townycitizens").setTabCompleter(command);
            townCommand = TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "citizens", command);
            if (!townCommand) getLogger().warning("/t citizens занят; используйте /townycitizens");
            getServer().getPluginManager().registerEvents(menus, this);
            getServer().getPluginManager().registerEvents(new CitizensListener(service), this);
            getServer().getServicesManager().register(TownyCitizensApi.class, service, this, ServicePriority.Normal);
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) unregisterPlaceholders = CitizensPlaceholders.register(this, service);
            getLogger().info("Реестр гражданства загружен. Четыре статуса; /t citizens.");
        } catch (Exception | LinkageError ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Гражданство не загружено; исходный реестр сохранён", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    private CitizensSettings readSettings() throws Exception {
        var yaml = new YamlConfiguration(); yaml.load(getDataFolder().toPath().resolve("config.yml").toFile()); return CitizensSettings.load(yaml);
    }
    public void reloadSettings() throws Exception { var next = readSettings(); service.settings(next); reloadConfig(); }
    public CitizensService citizens() { return service; }
    @Override public void onDisable() {
        if (unregisterPlaceholders != null) unregisterPlaceholders.run();
        getServer().getServicesManager().unregisterAll(this);
        if (townCommand) TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "citizens");
        for (var p : getServer().getOnlinePlayers()) if (p.getOpenInventory().getTopInventory().getHolder() != null
                && p.getOpenInventory().getTopInventory().getHolder().getClass().getName().startsWith("ru.neverland.townycitizens.")) p.closeInventory();
    }
}
