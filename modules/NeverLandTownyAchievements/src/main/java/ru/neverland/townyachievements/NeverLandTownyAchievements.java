package ru.neverland.townyachievements;

import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import ru.neverland.townyachievements.api.TownyAchievementsApi;
import ru.neverland.townyachievements.config.AchievementSettings;
import ru.neverland.townyachievements.data.AchievementRepository;
import ru.neverland.townyachievements.service.AchievementService;
import ru.neverland.townyachievements.gui.AchievementMenu;
import ru.neverland.townyachievements.command.AchievementCommand;

public final class NeverLandTownyAchievements extends JavaPlugin {
    private AchievementService service; private AchievementMenu menu; private boolean townCommand;
    private Runnable closeExpansion = () -> { };
    @Override public void onEnable() {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;
        try {
            saveDefaultConfig(); if (!new File(getDataFolder(), "achievements.yml").exists()) saveResource("achievements.yml", false);
            var repository = new AchievementRepository(getDataFolder().toPath().resolve("achievements-data.yml")); repository.load();
            service = new AchievementService(this, repository, settings()); menu = new AchievementMenu(this, service);
            var command = new AchievementCommand(this, service, menu);
            var direct = java.util.Objects.requireNonNull(getCommand("townyachievements")); direct.setExecutor(command); direct.setTabCompleter(command);
            ru.neverland.core.ActivityAdmin.attach(this, "townyachievements", "neverlandtownyachievements.admin", service::adminTargets);
            townCommand = TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "achievements", command);
            if (!townCommand) getLogger().warning("/t achievements занят; используйте /townyachievements");
            getServer().getPluginManager().registerEvents(menu, this);
            getServer().getServicesManager().register(TownyAchievementsApi.class, service, this, ServicePriority.Normal);
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                var expansion = new ru.neverland.townyachievements.integration.AchievementsExpansion(service, getPluginMeta().getVersion());
                if (expansion.register()) closeExpansion = expansion::unregister;
            }
            service.start();
        } catch (Exception | LinkageError ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Достижения приостановлены; сохранённые данные не перезаписаны", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    private AchievementSettings settings() throws Exception {
        var config = new YamlConfiguration(); config.load(new File(getDataFolder(), "config.yml"));
        var definitions = new YamlConfiguration(); definitions.load(new File(getDataFolder(), "achievements.yml"));
        return AchievementSettings.load(config, definitions);
    }
    public void reloadAchievements() throws Exception { var next = settings(); service.reload(next); menu.close(); }
    @Override public void onDisable() {
        if (!ru.neverland.core.ModuleLifecycle.end(this)) return;
        if (service != null) service.stop(); if (menu != null) menu.close(); closeExpansion.run();
        getServer().getServicesManager().unregisterAll(this);
        if (townCommand) TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "achievements");
    }
}
