package ru.neverland.townyarmy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townyarmy.api.TownyArmyApi;

public final class NeverLandTownyArmy extends JavaPlugin {
    private ArmyService service;
    private ArmyExpansion expansion;
    private BukkitTask task;
    private ArmySettings settings() throws Exception {
        var yaml = new YamlConfiguration(); yaml.load(new File(getDataFolder(), "config.yml"));
        try (var stream = getResource("config.yml")) { yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8))); }
        var settings = ArmySettings.load(yaml);
        for (var unit : settings.units().values()) if (!unit.icon().isItem()) throw new IllegalArgumentException("Иконка подразделения должна быть предметом");
        return settings;
    }
    public void reloadArmy() throws Exception { service.reload(settings()); reloadConfig(); }
    @Override public void onEnable() {
        try {
            saveDefaultConfig(); var settings = settings();
            var repository = new ArmyRepository(getDataFolder().toPath().resolve("army-data.yml"), settings.auditLimit()); repository.load();
            service = new ArmyService(this, repository, settings); service.migrate();
            var menu = new ArmyMenu(this, service); var command = new ArmyCommand(this, service, menu);
            getCommand("townymilitary").setExecutor(command); getCommand("townymilitary").setTabCompleter(command);
            getServer().getPluginManager().registerEvents(menu, this);
            getServer().getPluginManager().registerEvents(new ArmyListener(service), this);
            getServer().getServicesManager().register(TownyArmyApi.class, service, this, ServicePriority.Normal);
            task = getServer().getScheduler().runTaskTimer(this, service::pulse, 40L, 100L);
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) { expansion = new ArmyExpansion(service, getPluginMeta().getVersion()); expansion.register(); }
            // Builds owns /t army and /townyarmy and forwards to this module without a loading cycle.
        } catch (Exception | LinkageError ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Армия не загружена; проверьте данные и версии Builds/Resources", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    @Override public void onDisable() {
        if (task != null) task.cancel(); if (service != null) service.stop();
        if (expansion != null) expansion.unregister(); getServer().getServicesManager().unregisterAll(this);
    }
}
