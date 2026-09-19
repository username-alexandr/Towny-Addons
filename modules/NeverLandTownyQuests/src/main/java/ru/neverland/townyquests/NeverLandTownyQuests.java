package ru.neverland.townyquests;

import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.charset.StandardCharsets;
import ru.neverland.townyquests.api.TownyQuestsApi;
import ru.neverland.townyquests.command.QuestCommand;
import ru.neverland.townyquests.config.QuestSettings;
import ru.neverland.townyquests.data.QuestRepository;
import ru.neverland.townyquests.gui.QuestMenu;
import ru.neverland.townyquests.service.QuestService;

public final class NeverLandTownyQuests extends JavaPlugin {
    private QuestService service; private QuestMenu menu; private boolean townCommand;
    @Override public void onEnable() {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;
        try {
            saveDefaultConfig(); if (!new File(getDataFolder(), "quests.yml").exists()) saveResource("quests.yml", false);
            var repository = new QuestRepository(getDataFolder().toPath().resolve("quests-data.yml")); repository.load();
            service = new QuestService(this, repository, settings()); menu = new QuestMenu(service);
            var command = new QuestCommand(this, service, menu); var direct = java.util.Objects.requireNonNull(getCommand("townyquests"));
            direct.setExecutor(command); direct.setTabCompleter(command);
            ru.neverland.core.ActivityAdmin.attach(this, "townyquests", "neverlandtownyquests.admin", service::adminTargets);
            townCommand = TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "quests", command);
            if (!townCommand) getLogger().warning("/t quests занят; используйте /townyquests");
            getServer().getPluginManager().registerEvents(menu, this);
            getServer().getServicesManager().register(TownyQuestsApi.class, service, this, ServicePriority.Normal); service.start();
        } catch (Exception | LinkageError ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Городские проекты приостановлены. Сохранённые данные не перезаписаны.", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    private YamlConfiguration read(String name, boolean defaults) throws Exception {
        var y = new YamlConfiguration(); y.load(new File(getDataFolder(), name));
        if (defaults) try (var input = getResource(name)) {
            if (input != null) y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8)));
            y.options().copyDefaults(true);
        }
        return y;
    }
    private QuestSettings settings() throws Exception { return QuestSettings.load(read("config.yml", true), read("quests.yml", false)); }
    public void reloadQuests() throws Exception { var next = settings(); service.reload(next); menu.close(); }
    @Override public void onDisable() {
        if (!ru.neverland.core.ModuleLifecycle.end(this)) return;
        if (service != null) service.stop(); if (menu != null) menu.close();
        getServer().getServicesManager().unregisterAll(this);
        if (townCommand) TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "quests");
    }
}
