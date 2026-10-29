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
    private ru.neverland.townyquests.cooperative.CooperativeService cooperative;
    private ru.neverland.townyquests.cooperative.CooperativeMenu cooperativeMenu;
    private QuestService service; private QuestMenu menu; private boolean townCommand;
    @Override public void onEnable() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;
        try {
            saveDefaultConfig(); if (!new File(getDataFolder(), "quests.yml").exists()) saveResource("quests.yml", false);
            var repository = new QuestRepository(getDataFolder().toPath().resolve("quests-data.yml")); repository.load();
            service = new QuestService(this, repository, settings()); menu = new QuestMenu(this,service);
            var command = new QuestCommand(this, service, menu); var direct = java.util.Objects.requireNonNull(getCommand("townyquests"));
            direct.setExecutor(command); direct.setTabCompleter(command);
            try {
                cooperative=new ru.neverland.townyquests.cooperative.CooperativeService(this);
                cooperativeMenu=new ru.neverland.townyquests.cooperative.CooperativeMenu(this,service,cooperative);
                service.cooperative(cooperative);menu.cooperative(cooperativeMenu);command.cooperative(cooperativeMenu);
            } catch(Exception|LinkageError optional){
                if(cooperative!=null)cooperative.stop();cooperative=null;
                getLogger().log(java.util.logging.Level.WARNING,"Общие стройки недоступны; городские цепочки продолжают работать.",optional);
            }
            ru.neverland.core.ActivityAdmin.attach(this, "townyquests", "neverlandtownyquests.admin", service::adminTargets);
            townCommand = TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "quests", command);
            if (!townCommand) getLogger().warning("/t quests занят; используйте /townyquests");
            getServer().getPluginManager().registerEvents(menu, this);
            getServer().getServicesManager().register(TownyQuestsApi.class, service, this, ServicePriority.Normal); service.start();
            ru.neverland.core.ModuleShutdown.register(this,this::prepareShutdown);
        } catch (Exception | LinkageError ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Городские проекты приостановлены. Сохранённые данные не перезаписаны.", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    } }
    private YamlConfiguration read(String name, boolean defaults) throws Exception {
        var y = new YamlConfiguration(); y.load(new File(getDataFolder(), name));
        if (defaults) try (var input = getResource(name)) {
            if (input != null) y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8)));
            y.options().copyDefaults(true);
        }
        return y;
    }
    private QuestSettings settings() throws Exception { return QuestSettings.load(read("config.yml", true), read("quests.yml", true)); }
    public void reloadQuests() throws Exception { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { var next = settings(); service.reload(next); menu.close(); if(cooperative!=null)cooperative.reload();if(cooperativeMenu!=null)cooperativeMenu.close(); } }
    private java.util.concurrent.CompletionStage<Void> prepareShutdown(){
        if(cooperative!=null)cooperative.stop();
        return cooperativeMenu==null?java.util.concurrent.CompletableFuture.completedFuture(null):cooperativeMenu.closeAsync();
    }
    @Override public void onDisable() {
        try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
            if (!ru.neverland.core.ModuleLifecycle.end(this)) return;
            prepareShutdown();if(cooperativeMenu!=null)cooperativeMenu.close();
            if (service != null) service.stop(); if (menu != null) menu.close();
            getServer().getServicesManager().unregisterAll(this);
            if (townCommand) TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "quests");
        }
        // Control awaits this receipt before disable; full server stop has no outer suite guard.
        try{prepareShutdown().toCompletableFuture().get(5,java.util.concurrent.TimeUnit.SECONDS);}
        catch(InterruptedException error){Thread.currentThread().interrupt();getLogger().log(java.util.logging.Level.SEVERE,"Остановка журнала общих строек прервана",error);}
        catch(Exception error){getLogger().log(java.util.logging.Level.SEVERE,"Аудит общих строек не подтвердил завершение; сверяйте историю в Contracts",error);}
    }
}
