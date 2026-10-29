package ru.neverland.townyseasons;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import ru.neverland.townyseasons.api.TownySeasonsApi;

public final class NeverLandTownySeasons extends JavaPlugin {
    private SeasonService service;private BukkitTask task;private boolean townCommand;
    private ru.neverland.townyseasons.markets.SeasonMarketsService markets;
    private SeasonSettings settings() throws Exception {
        var y=new YamlConfiguration();y.load(new File(getDataFolder(),"config.yml"));
        try(var stream=getResource("config.yml")){y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8)));}
        return SeasonSettings.load(y);
    }
    public void reloadSeasons() throws Exception { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { var next=settings();service.reload(next);reloadConfig();if(markets!=null)markets.reload(); } }
    @Override public void onEnable() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;

        try {
            saveDefaultConfig();var settings=settings();var repository=new SeasonRepository(getDataFolder().toPath().resolve("calendar.yml"));repository.load(System.currentTimeMillis());
            service=new SeasonService(this,repository,settings);var menu=new SeasonMenu(this,service);var command=new SeasonCommand(this,service,menu);
            try {
                markets=new ru.neverland.townyseasons.markets.SeasonMarketsService(this,service);
                var stockMenu=new ru.neverland.townyseasons.markets.SeasonMarketsMenu(this,markets);
                service.marketView(markets::window);menu.markets(stockMenu);command.markets(markets,stockMenu);
                getServer().getPluginManager().registerEvents(stockMenu,this);
                ru.neverland.core.ModuleShutdown.register(this,markets::closeAsync);
            } catch(Exception|LinkageError optional) {getLogger().log(java.util.logging.Level.WARNING,"Сезонные запасы недоступны; обычный календарь продолжает работать",optional);}
            getCommand("townyseasons").setExecutor(command);getCommand("townyseasons").setTabCompleter(command);
            getServer().getPluginManager().registerEvents(menu,this);
            townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"seasons",command);
            if(!townCommand)getLogger().warning("/t seasons занят; используйте /tseasons");
            getServer().getServicesManager().register(TownySeasonsApi.class,service,this,ServicePriority.Normal);
            task=ru.neverland.core.SuiteScheduler.get().runTaskTimer(this,service::pulse,20,100);
        } catch(Exception|LinkageError e) {getLogger().log(java.util.logging.Level.SEVERE,"Сезоны не загружены",e);getServer().getPluginManager().disablePlugin(this);}
    } }
    @Override public void onDisable() {
        try (var guard = ru.neverland.core.SuiteState.guard()) {
            if (!ru.neverland.core.ModuleLifecycle.end(this)) return;
            if(task!=null)task.cancel();if(markets!=null)markets.closeAsync();
            getServer().getServicesManager().unregisterAll(this);
            if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"seasons");
        }
        if(markets!=null)markets.awaitClosed();ru.neverland.core.ModuleShutdown.remove(this);
    }
}
