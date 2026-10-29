package ru.neverland.townydiplomacy;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townydiplomacy.api.TownyDiplomacyApi;

public final class NeverLandTownyDiplomacy extends JavaPlugin {
    private DiplomacyService service;private boolean townCommand;private long warningAt;
    private ru.neverland.townydiplomacy.aid.AidService aid;private ru.neverland.townydiplomacy.aid.AidMenus aidMenus;private ru.neverland.townydiplomacy.aid.AidCommands aidCommands;
    public ru.neverland.townydiplomacy.aid.AidService aid(){return aid;}
    public ru.neverland.townydiplomacy.aid.AidMenus aidMenus(){return aidMenus;}
    public ru.neverland.townydiplomacy.aid.AidCommands aidCommands(){return aidCommands;}
    @Override public void onEnable() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;

        var repository=new DiplomacyRepository(getDataFolder().toPath().resolve("diplomacy.yml"));
        var settings=DiplomacySettings.defaults();boolean configured=false;
        try { saveDefaultConfig();settings=readSettings();configured=true; }
        catch(Exception ex) { failure("Некорректный config.yml; новые действия остановлены",ex); }
        try { repository.load(); } catch(Exception ex) { failure("Реестр не загружен. Исходный diplomacy.yml сохранён; межгородские действия закрыты",ex); }
        service=new DiplomacyService(repository,settings,configured);
        aid=new ru.neverland.townydiplomacy.aid.AidService(this,service);aidMenus=new ru.neverland.townydiplomacy.aid.AidMenus(this,aid);aidCommands=new ru.neverland.townydiplomacy.aid.AidCommands(this,aid,aidMenus);
        ru.neverland.core.ModuleShutdown.register(this,aid::closeAsync);
        var menus=new DiplomacyMenus(this,service);var command=new DiplomacyCommand(this,service,menus);
        getCommand("townydiplomacy").setExecutor(command);getCommand("townydiplomacy").setTabCompleter(command);
        townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"diplomacy",command);
        if(!townCommand)getLogger().warning("/t diplomacy занят; используйте /diplomacy");
        getServer().getPluginManager().registerEvents(aidMenus,this);getServer().getPluginManager().registerEvents(new ru.neverland.townydiplomacy.aid.AidCombatListener(this,service,aid),this);
        ru.neverland.core.ActivityAdmin.attach(this,"townydiplomacy","neverlandtownydiplomacy.admin",aid::adminTargets);aid.start();
        getServer().getPluginManager().registerEvents(menus,this);getServer().getPluginManager().registerEvents(new DiplomacyCombatListener(this,service),this);
        getServer().getServicesManager().register(TownyDiplomacyApi.class,service,this,ServicePriority.Normal);
        ru.neverland.core.SuiteScheduler.get().runTaskTimer(this,()->{if(service.healthy())try{service.maintain(System.currentTimeMillis());}catch(Exception ex){failure("Дипломатические изменения остановлены после ошибки сохранения",ex);}},1,20);
        getLogger().info("Дипломатия: семь типов отношений; /diplomacy. Реестр "+(service.healthy()?"доступен":"требует восстановления"));
    } }
    private DiplomacySettings readSettings()throws Exception { var y=new YamlConfiguration();y.load(getDataFolder().toPath().resolve("config.yml").toFile());return DiplomacySettings.load(y); }
    public void reloadSettings()throws Exception { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { var next=readSettings();service.settings(next);reloadConfig();if(aid!=null)aid.configure(); } }
    public DiplomacyService diplomacy() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return service; } }
    public void failure(String text,Exception ex) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { long now=System.currentTimeMillis();if(now-warningAt>60_000){warningAt=now;getLogger().log(java.util.logging.Level.SEVERE,text,ex);} } }
    public void announce(Treaty treaty,String text) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { notifyTown(treaty.first(),text);notifyTown(treaty.second(),text); } }
    public void notifyTown(java.util.UUID town,String text) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
        var t=TownyAPI.getInstance().getTown(town);if(t==null)return;
        for(var resident:t.getResidents()){var player=getServer().getPlayer(resident.getUUID());if(player!=null)DiplomacyCommand.tell(player,text);}
    } }
    @Override public void onDisable() {
        java.util.concurrent.CompletionStage<Void> closing=java.util.concurrent.CompletableFuture.completedFuture(null);
        try(var guard=ru.neverland.core.SuiteState.guard()) {
            if(!ru.neverland.core.ModuleLifecycle.end(this))return;
            if(aid!=null)closing=aid.closeAsync();
            ru.neverland.core.SuiteScheduler.get().cancelTasks(this);getServer().getServicesManager().unregisterAll(this);
            if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"diplomacy");
            ru.neverland.core.SuiteScheduler.cleanupPlayers(this,p->{var h=p.getOpenInventory().getTopInventory().getHolder();if(h instanceof DiplomacyMenus.Holder||h instanceof ru.neverland.townydiplomacy.aid.AidMenus.Holder)p.closeInventory();});
        }
        try{closing.toCompletableFuture().get(10,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception ex){getLogger().log(java.util.logging.Level.SEVERE,"Запись помощи ещё не подтверждена; исходные данные сохранены",ex);}finally{ru.neverland.core.ModuleShutdown.remove(this);}
    }
}
