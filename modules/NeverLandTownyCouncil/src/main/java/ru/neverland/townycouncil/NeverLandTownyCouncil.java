package ru.neverland.townycouncil;

import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.event.TownRemoveResidentEvent;
import com.palmergames.bukkit.towny.event.town.TownMayorChangedEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townycouncil.api.TownyCouncilApi;

public final class NeverLandTownyCouncil extends JavaPlugin implements Listener {
    private CouncilService service;
    private boolean townCommand;
    @Override public void onEnable() { synchronized (ru.neverland.core.SuiteState.lock()) {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;

        try {
            saveDefaultConfig();
            var repository = new CouncilRepository(getDataFolder().toPath().resolve("council.yml")); repository.load();
            var access = new AccessRepository(getDataFolder().toPath().resolve("access.yml")); access.load();
            service = new CouncilService(this, repository, readSettings(), access);
            var menus = new CouncilMenus(this, service);var accessMenus=new AccessMenus(this,service,menus);menus.access(accessMenus); var command = new CouncilCommand(this, service, menus,accessMenus);
            getServer().getPluginManager().registerEvents(accessMenus,this);
            getCommand("townycouncil").setExecutor(command); getCommand("townycouncil").setTabCompleter(command);
            townCommand = TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "ministers", command);
            if (!townCommand) getLogger().warning("/t ministers занят; используйте /council");
            getServer().getPluginManager().registerEvents(this, this); getServer().getPluginManager().registerEvents(menus, this);
            getServer().getServicesManager().register(TownyCouncilApi.class, service, this, ServicePriority.Normal);
            ru.neverland.core.SuiteScheduler.get().runTaskTimer(this, () -> guarded(() -> { service.prune(); service.refreshAll(); }), 1, 20);
            getLogger().info("Четыре министерства готовы; /council и /t ministers.");
        } catch (Exception | LinkageError ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Реестр совета не загружен; исходный файл сохранён", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    } }
    private CouncilSettings readSettings() throws Exception {
        var y = new YamlConfiguration(); y.load(getDataFolder().toPath().resolve("config.yml").toFile()); return CouncilSettings.load(y);
    }
    public void reloadSettings() throws Exception { synchronized (ru.neverland.core.SuiteState.lock()) { var next = readSettings(); service.settings(next); reloadConfig(); } }
    public CouncilService council() { synchronized (ru.neverland.core.SuiteState.lock()) { return service; } }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private boolean failureReported;
    private void guarded(Action action) {
        try { action.run(); }
        catch (Exception ex) {
            service.clearGrants();
            if (!failureReported) { failureReported = true; getLogger().log(java.util.logging.Level.SEVERE, "Права совета отозваны: проверьте реестр и перезапустите сервер", ex); }
        }
    }
    @EventHandler public void join(PlayerJoinEvent e) { synchronized (ru.neverland.core.SuiteState.lock()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyCouncil")){ru.neverland.core.ModuleGate.stopEvent(e);return;} guarded(() -> service.refresh(e.getPlayer())); } }
    @EventHandler public void quit(PlayerQuitEvent e) { synchronized (ru.neverland.core.SuiteState.lock()) { service.release(e.getPlayer().getUniqueId()); } }
    @EventHandler(priority = EventPriority.MONITOR) public void leave(TownRemoveResidentEvent e) { synchronized (ru.neverland.core.SuiteState.lock()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyCouncil")){ru.neverland.core.ModuleGate.stopEvent(e);return;}
        guarded(() -> {service.clearAccess(e.getTown().getUUID(),e.getResident().getUUID(),"LEFT_TOWN");service.remove(a -> a.town().equals(e.getTown().getUUID()) && a.resident().equals(e.getResident().getUUID()), "SYSTEM", "LEFT_TOWN");});
    } }
    @EventHandler(priority = EventPriority.MONITOR) public void mayor(TownMayorChangedEvent e) { synchronized (ru.neverland.core.SuiteState.lock()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyCouncil")){ru.neverland.core.ModuleGate.stopEvent(e);return;}
        if (e.getTown() == null || e.getOldMayor() == e.getNewMayor()) return;
        guarded(() -> {service.clearAccess(e.getTown().getUUID(),null,"MAYOR_CHANGED");service.remove(a -> a.town().equals(e.getTown().getUUID()), "SYSTEM", "MAYOR_CHANGED");});
    } }
    @Override public void onDisable() { synchronized (ru.neverland.core.SuiteState.lock()) {
        if (!ru.neverland.core.ModuleLifecycle.end(this)) return;

        ru.neverland.core.SuiteScheduler.get().cancelTasks(this);
        if (service != null) service.clearGrants();
        getServer().getServicesManager().unregisterAll(this);
        if (townCommand) TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "ministers");
        ru.neverland.core.SuiteScheduler.cleanupPlayers(this,p->{if(p.getOpenInventory().getTopInventory().getHolder() instanceof CouncilMenus.Holder||p.getOpenInventory().getTopInventory().getHolder() instanceof AccessMenus.View)p.closeInventory();});
    } }
}
