package ru.neverland.townyresearch;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townyresearch.api.TownyResearchApi;
import ru.neverland.townyresearch.config.ResearchSettings;
import ru.neverland.townyresearch.data.ResearchRepository;
import ru.neverland.townyresearch.service.ResearchService;
import ru.neverland.townyresearch.command.ResearchCommand;
import ru.neverland.townyresearch.gui.ResearchMenu;
import ru.neverland.townyresearch.integration.ResearchExpansion;
import ru.neverland.townyresearch.licenses.LicenseRepository;
import ru.neverland.townyresearch.licenses.LicenseSettings;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class NeverLandTownyResearch extends JavaPlugin {
    private final java.util.concurrent.atomic.AtomicBoolean reloading=new java.util.concurrent.atomic.AtomicBoolean();private ResearchService service;private boolean townCommand;private ResearchExpansion expansion;
    @Override public void onEnable(){
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;
try{
        saveDefaultConfig();if(!new File(getDataFolder(),"technologies.yml").exists())saveResource("technologies.yml",false);
        var repository=new ResearchRepository(getDataFolder().toPath().resolve("research-data.yml"));repository.load();
        var researchSettings=settings();var licenseRepository=new LicenseRepository(getDataFolder().toPath().resolve("licenses.yml"));
        // The optional journal fails closed independently; permanent studies still start.
        try{licenseRepository.load();}catch(Exception|LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Технологические лицензии приостановлены: licenses.yml не загружен. Файл сохранён без изменений; обычные исследования продолжают работать. После исправления журнала перезапустите сервер.",ex);}
        service=new ResearchService(this,repository,researchSettings,licenseRepository,licenseSettings(getConfig()));
        var menu=new ResearchMenu(this,service);var command=new ResearchCommand(this,service,menu);var direct=getCommand("townyresearch");if(direct==null)throw new IllegalStateException("Нет команды townyresearch");direct.setExecutor(command);direct.setTabCompleter(command);
        ru.neverland.core.ActivityAdmin.attach(this,"townyresearch","neverlandtownyresearch.admin",service::adminTargets);
        townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"research",command);if(!townCommand)getLogger().warning("/t research занят; используйте /townyresearch");
        getServer().getPluginManager().registerEvents(menu,this);getServer().getPluginManager().registerEvents(new ru.neverland.townyresearch.integration.WallProtection(service),this);service.start();getServer().getServicesManager().register(TownyResearchApi.class,service,this,ServicePriority.Normal);
        if(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")){expansion=new ResearchExpansion(service,getPluginMeta().getVersion());expansion.register();}
        getLogger().info("Исследования включены: "+service.settings().technologies().size()+" профилей.");
    }catch(Exception|LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Исследования не загружены. Данные не перезаписаны; исследования приостановлены до исправления причины.",ex);getServer().getPluginManager().disablePlugin(this);}}
    private YamlConfiguration read(String name)throws Exception{var y=new YamlConfiguration();y.load(new File(getDataFolder(),name));try(var input=getResource(name)){if(input!=null)y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(input,StandardCharsets.UTF_8)));}y.options().copyDefaults(true);return y;}
    private ResearchSettings settings()throws Exception{return ResearchSettings.load(read("config.yml"),read("technologies.yml"));}
    private LicenseSettings licenseSettings(org.bukkit.configuration.ConfigurationSection config){
        try{return LicenseSettings.load(config);}catch(Exception|LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Технологические лицензии приостановлены: проверьте раздел licenses в config.yml. Обычные исследования продолжают работать; данные лицензий сохранены.",ex);return new LicenseSettings(false,86400,16);}
    }
    public java.util.concurrent.CompletionStage<Boolean> reloadResearch(){if(!reloading.compareAndSet(false,true))return java.util.concurrent.CompletableFuture.completedFuture(false);return java.util.concurrent.CompletableFuture.supplyAsync(()->{try{var config=read("config.yml");var technologies=read("technologies.yml");var next=ResearchSettings.load(config,technologies);var licenses=licenseSettings(config);try(var guard=ru.neverland.core.SuiteState.guard()){if(!isEnabled())return false;service.reload(next);service.licenses().reload(licenses);return true;}}catch(Exception e){getLogger().warning("Настройки исследований не применены: "+e.getMessage());return false;}finally{reloading.set(false);}});}
    @Override public void onDisable(){java.util.concurrent.CompletionStage<Void> closing=java.util.concurrent.CompletableFuture.completedFuture(null);
        try(var guard=ru.neverland.core.SuiteState.guard()){if(!ru.neverland.core.ModuleLifecycle.end(this))return;if(expansion!=null)expansion.unregister();if(service!=null)closing=service.stop();getServer().getServicesManager().unregisterAll(this);if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"research");}
        var pending=closing.toCompletableFuture();if(ru.neverland.core.SuiteState.held()&&!pending.isDone()){pending.whenCompleteAsync((v,e)->{if(e!=null)getLogger().log(java.util.logging.Level.SEVERE,"Отложенная квитанция лицензии требует проверки",e);});return;}
        try{pending.get(10,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"Лицензии не подтвердили завершение записи; не повторяйте выплату вручную",e);}
    }
}
