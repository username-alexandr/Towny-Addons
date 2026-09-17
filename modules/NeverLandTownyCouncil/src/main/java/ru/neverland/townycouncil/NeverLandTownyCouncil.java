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
    @Override public void onEnable() {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;

        try {
            saveDefaultConfig();
            var repository = new CouncilRepository(getDataFolder().toPath().resolve("council.yml")); repository.load();
            service = new CouncilService(this, repository, readSettings());
            var menus = new CouncilMenus(this, service); var command = new CouncilCommand(this, service, menus);
            getCommand("townycouncil").setExecutor(command); getCommand("townycouncil").setTabCompleter(command);
            townCommand = TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "ministers", command);
            if (!townCommand) getLogger().warning("/t ministers занят; используйте /council");
            getServer().getPluginManager().registerEvents(this, this); getServer().getPluginManager().registerEvents(menus, this);
            getServer().getServicesManager().register(TownyCouncilApi.class, service, this, ServicePriority.Normal);
            getServer().getScheduler().runTaskTimer(this, () -> guarded(() -> { service.prune(); service.refreshAll(); }), 1, 20);
            getLogger().info("Четыре министерства готовы; /council и /t ministers.");
        } catch (Exception | LinkageError ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Реестр совета не загружен; исходный файл сохранён", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    private CouncilSettings readSettings() throws Exception {
        var y = new YamlConfiguration(); y.load(getDataFolder().toPath().resolve("config.yml").toFile()); return CouncilSettings.load(y);
    }
    public void reloadSettings() throws Exception { var next = readSettings(); service.settings(next); reloadConfig(); }
    public CouncilService council() { return service; }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private boolean failureReported;
    private void guarded(Action action) {
        try { action.run(); }
        catch (Exception ex) {
            service.clearGrants();
            if (!failureReported) { failureReported = true; getLogger().log(java.util.logging.Level.SEVERE, "Права совета отозваны: проверьте реестр и перезапустите сервер", ex); }
        }
    }
    @EventHandler public void join(PlayerJoinEvent e) { guarded(() -> service.refresh(e.getPlayer())); }
    @EventHandler public void quit(PlayerQuitEvent e) { service.release(e.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR) public void leave(TownRemoveResidentEvent e) {
        guarded(() -> service.remove(a -> a.town().equals(e.getTown().getUUID()) && a.resident().equals(e.getResident().getUUID()), "SYSTEM", "LEFT_TOWN"));
    }
    @EventHandler(priority = EventPriority.MONITOR) public void mayor(TownMayorChangedEvent e) {
        if (e.getTown() == null || e.getOldMayor() == e.getNewMayor()) return;
        guarded(() -> service.remove(a -> a.town().equals(e.getTown().getUUID()), "SYSTEM", "MAYOR_CHANGED"));
    }
    @Override public void onDisable() {
        if (!ru.neverland.core.ModuleLifecycle.end(this)) return;

        getServer().getScheduler().cancelTasks(this);
        if (service != null) service.clearGrants();
        getServer().getServicesManager().unregisterAll(this);
        if (townCommand) TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "ministers");
        for (var p : getServer().getOnlinePlayers()) if (p.getOpenInventory().getTopInventory().getHolder() instanceof CouncilMenus.Holder) p.closeInventory();
    }
}
