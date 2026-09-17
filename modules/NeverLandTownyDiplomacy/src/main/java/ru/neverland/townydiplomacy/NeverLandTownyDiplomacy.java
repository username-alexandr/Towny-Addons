package ru.neverland.townydiplomacy;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townydiplomacy.api.TownyDiplomacyApi;

public final class NeverLandTownyDiplomacy extends JavaPlugin {
    private DiplomacyService service;private boolean townCommand;private long warningAt;
    @Override public void onEnable() {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;

        var repository=new DiplomacyRepository(getDataFolder().toPath().resolve("diplomacy.yml"));
        var settings=DiplomacySettings.defaults();boolean configured=false;
        try { saveDefaultConfig();settings=readSettings();configured=true; }
        catch(Exception ex) { failure("Некорректный config.yml; новые действия остановлены",ex); }
        try { repository.load(); } catch(Exception ex) { failure("Реестр не загружен. Исходный diplomacy.yml сохранён; межгородские действия закрыты",ex); }
        service=new DiplomacyService(repository,settings,configured);var menus=new DiplomacyMenus(this,service);var command=new DiplomacyCommand(this,service,menus);
        getCommand("townydiplomacy").setExecutor(command);getCommand("townydiplomacy").setTabCompleter(command);
        townCommand=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"diplomacy",command);
        if(!townCommand)getLogger().warning("/t diplomacy занят; используйте /diplomacy");
        getServer().getPluginManager().registerEvents(menus,this);getServer().getPluginManager().registerEvents(new DiplomacyCombatListener(this,service),this);
        getServer().getServicesManager().register(TownyDiplomacyApi.class,service,this,ServicePriority.Normal);
        getServer().getScheduler().runTaskTimer(this,()->{if(service.healthy())try{service.maintain(System.currentTimeMillis());}catch(Exception ex){failure("Дипломатические изменения остановлены после ошибки сохранения",ex);}},1,20);
        getLogger().info("Дипломатия: семь типов отношений; /diplomacy. Реестр "+(service.healthy()?"доступен":"требует восстановления"));
    }
    private DiplomacySettings readSettings()throws Exception { var y=new YamlConfiguration();y.load(getDataFolder().toPath().resolve("config.yml").toFile());return DiplomacySettings.load(y); }
    public void reloadSettings()throws Exception { var next=readSettings();service.settings(next);reloadConfig(); }
    public DiplomacyService diplomacy() { return service; }
    public void failure(String text,Exception ex) { long now=System.currentTimeMillis();if(now-warningAt>60_000){warningAt=now;getLogger().log(java.util.logging.Level.SEVERE,text,ex);} }
    public void announce(Treaty treaty,String text) { notifyTown(treaty.first(),text);notifyTown(treaty.second(),text); }
    public void notifyTown(java.util.UUID town,String text) {
        var t=TownyAPI.getInstance().getTown(town);if(t==null)return;
        for(var resident:t.getResidents()){var player=getServer().getPlayer(resident.getUUID());if(player!=null)DiplomacyCommand.tell(player,text);}
    }
    @Override public void onDisable() {
        if (!ru.neverland.core.ModuleLifecycle.end(this)) return;

        getServer().getScheduler().cancelTasks(this);getServer().getServicesManager().unregisterAll(this);
        if(townCommand)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"diplomacy");
        for(var p:getServer().getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof DiplomacyMenus.Holder)p.closeInventory();
    }
}
