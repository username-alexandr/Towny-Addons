package ru.neverland.townypopulation;

import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townypopulation.api.TownyPopulationApi;
import ru.neverland.townypopulation.command.PopulationCommand;
import ru.neverland.townypopulation.config.PopulationSettings;
import ru.neverland.townypopulation.data.PopulationRepository;
import ru.neverland.townypopulation.gui.PopulationMenu;
import ru.neverland.townypopulation.integration.PopulationExpansion;
import ru.neverland.townypopulation.service.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class NeverLandTownyPopulation extends JavaPlugin {
    private PopulationService service;
    private Messages messages;
    private Runnable unregisterPlaceholder;
    private boolean townCommandRegistered;
    @Override public void onEnable() {
        try {
            saveDefaultConfig();
            for(String file : new String[]{"buildings.yml","messages.yml"})
                if(!new File(getDataFolder(),file).exists()) saveResource(file,false);
            var settings=readSettings(); messages=new Messages(read("messages.yml"));
            var repository=new PopulationRepository(getDataFolder().toPath().resolve("population-data.yml"));
            repository.load();
            service=new PopulationService(this,repository,settings);
            var menu=new PopulationMenu(this,service,messages);
            var command=new PopulationCommand(this,service,menu,messages);
            var direct=getCommand("townypopulation");
            if(direct==null) throw new IllegalStateException("Команда townypopulation не объявлена");
            direct.setExecutor(command); direct.setTabCompleter(command);
            townCommandRegistered=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"population",command);
            if(!townCommandRegistered) getLogger().warning("/t population занят другим плагином; доступен /townypopulation.");
            getServer().getPluginManager().registerEvents(menu,this);
            service.start();
            getServer().getServicesManager().register(TownyPopulationApi.class,service,this,ServicePriority.Normal);
            if(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) registerPlaceholders();
            getLogger().info("NeverLandTownyPopulation "+getPluginMeta().getVersion()+" включён: "+service.populations().size()+" городов.");
        } catch(Exception | LinkageError ex) {
            getLogger().log(java.util.logging.Level.SEVERE,"Не удалось загрузить население; проверьте настройки и базу.",ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    private void registerPlaceholders() {
        try {
            var expansion=new PopulationExpansion(service,getPluginMeta().getVersion());
            if(expansion.register()) unregisterPlaceholder=expansion::unregister;
        } catch(LinkageError ex) { getLogger().warning("PlaceholderAPI несовместим: "+ex.getMessage()); }
    }
    public boolean reloadPopulation() {
        try {
            var settings=readSettings(); var newMessages=read("messages.yml");
            service.reload(settings); messages.reload(newMessages); return true;
        } catch(Exception ex) {
            getLogger().warning("Настройки населения не применены: "+ex.getMessage()); return false;
        }
    }
    private PopulationSettings readSettings() throws Exception { return PopulationSettings.load(read("config.yml"),read("buildings.yml")); }
    private YamlConfiguration read(String file) throws Exception {
        YamlConfiguration result=new YamlConfiguration(); result.load(new File(getDataFolder(),file));
        // Missing fields inherit packaged defaults; administrator values and custom project IDs win.
        try(var stream=getResource(file)) {
            if(stream!=null) result.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8)));
        }
        result.options().copyDefaults(true);
        return result;
    }
    @Override public void onDisable() {
        if(service!=null) service.stop();
        if(unregisterPlaceholder!=null) unregisterPlaceholder.run();
        if(townCommandRegistered) TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"population");
        getServer().getServicesManager().unregisterAll(this);
    }
}
