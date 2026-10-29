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
    private DelegationRepository delegationStore;private DelegationMenus delegationMenus;
    private ru.neverland.townycouncil.spending.SpendingService spending;
    private ru.neverland.townycouncil.spending.SpendingMenus spendingMenus;
    private boolean townCommand;
    @Override public void onEnable() {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;

        try {
            saveDefaultConfig();
            var repository = new CouncilRepository(getDataFolder().toPath().resolve("council.yml")); repository.load();
            var access = new AccessRepository(getDataFolder().toPath().resolve("access.yml")); access.load();
            service = new CouncilService(this, repository, readSettings(), access);
            delegationStore=new DelegationRepository(getDataFolder().toPath().resolve("delegations.yml"));
            var spendingStore=new ru.neverland.townycouncil.spending.SpendingRepository(getDataFolder().toPath().resolve("spending-limits.yml"));
            spending=new ru.neverland.townycouncil.spending.SpendingService(this,service,spendingStore);service.spending(spending);
            ru.neverland.core.ModuleShutdown.register(this,this::prepareClose);
            delegationStore.load();
            try{spendingStore.load();}catch(Exception ex){getLogger().log(java.util.logging.Level.SEVERE,"Ограничения расходов недоступны; новые ручные списания казны приостановлены. Другие функции совета продолжают работать; исходный файл сохранён",ex);}
            var delegations=new DelegationService(this,service,delegationStore);service.delegations(delegations);
            delegationMenus=new DelegationMenus(this,service,delegations);getServer().getPluginManager().registerEvents(delegationMenus,this);
            spendingMenus=new ru.neverland.townycouncil.spending.SpendingMenus(this,service,spending);getServer().getPluginManager().registerEvents(spendingMenus,this);
            var menus = new CouncilMenus(this, service);var accessMenus=new AccessMenus(this,service,menus);menus.access(accessMenus); var command = new CouncilCommand(this, service, menus,accessMenus);
            var ministerMenus=new MinisterMenus(this,service,menus,accessMenus);menus.ministers(ministerMenus);menus.delegations(delegationMenus);command.delegations(delegationMenus);
            menus.spending(spendingMenus);command.spending(spendingMenus);
            getServer().getPluginManager().registerEvents(ministerMenus,this);
            getServer().getPluginManager().registerEvents(accessMenus,this);
            getCommand("townycouncil").setExecutor(command); getCommand("townycouncil").setTabCompleter(command);
            townCommand = TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "ministers", command);
            if (!townCommand) getLogger().warning("/t ministers занят; используйте /council");
            getServer().getPluginManager().registerEvents(this, this); getServer().getPluginManager().registerEvents(menus, this);
            getServer().getServicesManager().register(TownyCouncilApi.class, service, this, ServicePriority.Normal);
            ru.neverland.core.SuiteScheduler.get().runTaskTimer(this, () -> guarded(() -> { service.prune(); service.refreshAll(); }), 1, 20);
            ru.neverland.core.SuiteScheduler.get().runTaskTimer(this,spending::pulse,1,20);
            getLogger().info("Четыре министерства готовы; /council и /t ministers.");
        } catch (Exception | LinkageError ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Реестр совета не загружен; исходный файл сохранён", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    private java.util.concurrent.CompletionStage<Void> prepareClose(){
        var rights=delegationStore==null?java.util.concurrent.CompletableFuture.<Void>completedFuture(null):delegationStore.prepareClose().toCompletableFuture();
        var limits=spending==null?java.util.concurrent.CompletableFuture.<Void>completedFuture(null):spending.close().toCompletableFuture();
        return java.util.concurrent.CompletableFuture.allOf(rights,limits);
    }
    private void awaitClosed(){var pending=prepareClose().toCompletableFuture();if(ru.neverland.core.SuiteState.held()&&!pending.isDone()){pending.whenCompleteAsync((v,error)->{if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"Отложенная запись совета требует проверки",error);});return;}try{pending.get(10,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException("Ожидание сохранения совета прервано",ex);}catch(java.util.concurrent.ExecutionException|java.util.concurrent.TimeoutException ex){throw new IllegalStateException("Сохранение совета ещё не подтверждено",ex);}}
    private CouncilSettings readSettings() throws Exception {
        var y = new YamlConfiguration(); y.load(getDataFolder().toPath().resolve("config.yml").toFile()); return CouncilSettings.load(y);
    }
    public void reloadSettings() throws Exception { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { var next = readSettings(); service.settings(next); reloadConfig(); } }
    public CouncilService council() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return service; } }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private boolean failureReported;
    private void guarded(Action action) {
        try { action.run(); }
        catch (Exception ex) {
            service.clearGrants();
            if (!failureReported) { failureReported = true; getLogger().log(java.util.logging.Level.SEVERE, "Права совета отозваны: проверьте реестр и перезапустите сервер", ex); }
        }
    }
    @EventHandler public void join(PlayerJoinEvent e) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyCouncil")){ru.neverland.core.ModuleGate.stopEvent(e);return;} guarded(() -> service.refresh(e.getPlayer())); } }
    @EventHandler public void quit(PlayerQuitEvent e) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { service.release(e.getPlayer().getUniqueId()); } }
    @EventHandler(priority = EventPriority.MONITOR) public void leave(TownRemoveResidentEvent e) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyCouncil")){ru.neverland.core.ModuleGate.stopEvent(e);return;}
        guarded(() -> {service.clearAccess(e.getTown().getUUID(),e.getResident().getUUID(),"LEFT_TOWN");service.remove(a -> a.town().equals(e.getTown().getUUID()) && a.resident().equals(e.getResident().getUUID()), "SYSTEM", "LEFT_TOWN");});
    } }
    @EventHandler(priority = EventPriority.MONITOR) public void mayor(TownMayorChangedEvent e) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyCouncil")){ru.neverland.core.ModuleGate.stopEvent(e);return;}
        if (e.getTown() == null || e.getOldMayor() == e.getNewMayor()) return;
        guarded(() -> {service.clearAccess(e.getTown().getUUID(),null,"MAYOR_CHANGED");service.remove(a -> a.town().equals(e.getTown().getUUID()), "SYSTEM", "MAYOR_CHANGED");});
    } }
    @Override public void onDisable() { try { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
        if (!ru.neverland.core.ModuleLifecycle.end(this)) return;

        ru.neverland.core.SuiteScheduler.get().cancelTasks(this);
        prepareClose();if(delegationMenus!=null)delegationMenus.close();if(spendingMenus!=null)spendingMenus.close();
        if (service != null) service.clearGrants();
        getServer().getServicesManager().unregisterAll(this);
        if (townCommand) TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN, "ministers");
        ru.neverland.core.SuiteScheduler.cleanupPlayers(this,p->{var holder=p.getOpenInventory().getTopInventory().getHolder();if(holder instanceof CouncilMenus.Holder||holder instanceof AccessMenus.View||holder instanceof MinisterMenus.View||holder!=null&&(holder.getClass().getName().contains(".DelegationMenus$")||holder.getClass().getName().contains(".SpendingMenus$")))p.closeInventory();});
    } } finally {try{awaitClosed();}finally{ru.neverland.core.ModuleShutdown.remove(this);}} }
}
