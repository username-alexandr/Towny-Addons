package ru.neverland.townyenvironment;

import java.io.File;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townyenvironment.api.TownyEnvironmentApi;
import ru.neverland.townyenvironment.config.EnvironmentSettings;
import ru.neverland.townyenvironment.data.EnvironmentRepository;
import ru.neverland.townyenvironment.service.EnvironmentService;
import ru.neverland.townyenvironment.gui.EnvironmentMenu;
import ru.neverland.townyenvironment.command.EnvironmentCommand;

public final class NeverLandTownyEnvironment extends JavaPlugin {
    private EnvironmentService service; private EnvironmentMenu menu; private boolean townCommand;
    @Override public void onEnable() {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;
        try {
            saveDefaultConfig(); var repository = new EnvironmentRepository(getDataFolder().toPath().resolve("environment-data.yml")); repository.load();
            service = new EnvironmentService(this, repository, settings()); menu = new EnvironmentMenu(service);
            var command = new EnvironmentCommand(this, menu); var direct = java.util.Objects.requireNonNull(getCommand("townyenvironment"));
            direct.setExecutor(command); direct.setTabCompleter(command);
            ru.neverland.core.ActivityAdmin.attach(this, "townyenvironment", "neverlandtownyenvironment.admin", service::adminTargets);
            townCommand = TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "environment", command);
            if (!townCommand) getLogger().warning("/t environment занят; используйте /townyenvironment");
            getServer().getPluginManager().registerEvents(menu, this);
            getServer().getServicesManager().register(TownyEnvironmentApi.class, service, this, ServicePriority.Normal); service.start();
        } catch (Exception | LinkageError ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Экология остановлена; сохранённые данные не перезаписаны", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    private EnvironmentSettings settings() throws Exception {
        var yaml = new YamlConfiguration(); yaml.load(new File(getDataFolder(), "config.yml")); return EnvironmentSettings.load(yaml);
    }
    public void reloadEnvironment() throws Exception { var next = settings(); service.reload(next); menu.close(); }
    @Override public void onDisable() {
        if (!ru.neverland.core.ModuleLifecycle.end(this)) return;
        if (service != null) service.stop(); if (menu != null) menu.close(); getServer().getServicesManager().unregisterAll(this);
        if (townCommand) TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "environment");
    }
}
