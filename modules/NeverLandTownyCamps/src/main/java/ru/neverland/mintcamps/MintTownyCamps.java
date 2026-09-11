package ru.neverland.mintcamps;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.mintcamps.command.AdminCommand;
import ru.neverland.mintcamps.command.CampCommand;
import ru.neverland.mintcamps.data.CampRepository;
import ru.neverland.mintcamps.data.CostStore;
import ru.neverland.mintcamps.gui.MenuManager;
import ru.neverland.mintcamps.integration.ItemsAdderHook;
import ru.neverland.mintcamps.integration.MintCampsExpansion;
import ru.neverland.mintcamps.integration.TownyHook;
import ru.neverland.mintcamps.listener.CampProtectionListener;
import ru.neverland.mintcamps.model.Camp;
import ru.neverland.mintcamps.service.CampService;
import ru.neverland.mintcamps.service.FuelService;
import ru.neverland.mintcamps.service.HologramService;
import ru.neverland.mintcamps.service.MessageService;
import ru.neverland.mintcamps.service.ResourceService;
import ru.neverland.mintcamps.service.RussianItemNames;
import ru.neverland.mintcamps.service.StructureGenerator;
import ru.neverland.mintcamps.service.StructureService;
import ru.neverland.mintcamps.service.StyleRegistry;
import ru.neverland.mintcamps.service.TeleportService;

import java.io.File;
import java.util.ArrayList;

public final class MintTownyCamps extends JavaPlugin {
    private CampRepository repository;
    private CostStore costs;
    private MessageService messages;
    private StyleRegistry styles;
    private RussianItemNames itemNames;
    private FuelService fuel;
    private TownyHook towny;
    private ItemsAdderHook itemsAdder;
    private CampService camps;
    private HologramService holograms;
    private TeleportService teleports;
    private CampProtectionListener protection;
    private BukkitTask autosaveTask;
    private BukkitTask expiryTask;
    private MintCampsExpansion expansion;

    @Override
    public void onEnable() {
        ru.neverland.mintcamps.util.LegacyDataMigrator.migrate(this, "MintTownyCamps");
        saveDefaultConfig();
        copyResource("messages.yml");
        copyResource("styles.yml");
        copyResource("upgrade-costs.yml");
        copyResource("item-names.yml");

        messages = new MessageService(this);
        styles = new StyleRegistry(this);
        itemNames = new RussianItemNames(this);
        costs = new CostStore(this);
        repository = new CampRepository(this);
        repository.load();
        Bukkit.getServicesManager().register(ru.neverland.mintcamps.api.TownyCampsApi.class,new ru.neverland.mintcamps.service.CampsApiService(repository),this,org.bukkit.plugin.ServicePriority.Normal);
        ResourceService resources = new ResourceService();
        StructureGenerator generator = new StructureGenerator();
        StructureService structures = new StructureService(this, generator);
        towny = new TownyHook(this);
        itemsAdder = new ItemsAdderHook(this);
        fuel = new FuelService(this);
        camps = new CampService(this, repository, costs, resources, messages, styles, structures,
                generator, towny, itemNames);
        teleports = new TeleportService(this, repository, messages);
        MenuManager menus = new MenuManager(this, repository, costs, camps, fuel, teleports,
                messages, itemNames, resources, itemsAdder);
        holograms = new HologramService(this, repository, messages, camps);
        camps.holograms(holograms);
        protection = new CampProtectionListener(this, repository, messages);
        camps.migrateRaisedCampfires();

        Bukkit.getPluginManager().registerEvents(teleports, this);
        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(protection, this);
        Bukkit.getPluginManager().registerEvents(holograms, this);
        CampCommand campCommand = new CampCommand(repository, camps, menus, teleports, messages);
        bind("camp", campCommand, campCommand);
        AdminCommand adminCommand = new AdminCommand(this, repository, camps, menus, messages);
        bind("townycamps", adminCommand, adminCommand);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                expansion = new MintCampsExpansion(this, repository, camps, fuel);
                expansion.register();
                getLogger().info("PlaceholderAPI обнаружен: зарегистрировано 10 плейсхолдеров.");
            } catch (LinkageError error) {
                expansion = null;
                getLogger().warning("PlaceholderAPI несовместим: " + error.getMessage());
            }
        }
        startTasks();
        holograms.start();
        protection.start();
        getLogger().info("NeverLandTownyCamps 0.1.6 включён: процедурные лагеря готовы.");
    }

    @Override
    public void onDisable() {
        Bukkit.getServicesManager().unregisterAll(this);
        stopTasks();
        if (protection != null) protection.stop();
        if (holograms != null) holograms.stop(true);
        if (teleports != null) teleports.cancelAll();
        if (repository != null && repository.writable()) repository.save();
        if (expansion != null) expansion.unregister();
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
        styles.reload();
        itemNames.reload();
        costs.reload();
        fuel.reload();
        towny.reload();
        itemsAdder.reload();
        startTasks();
        holograms.start();
        protection.start();
    }

    private void startTasks() {
        stopTasks();
        long autosave = Math.max(20L, getConfig().getLong("settings.data.autosave-seconds", 60L) * 20L);
        autosaveTask = Bukkit.getScheduler().runTaskTimer(this, repository::saveIfDirty, autosave, autosave);
        long expiry = Math.max(20L, getConfig().getLong("settings.data.expiry-check-seconds", 20L) * 20L);
        expiryTask = Bukkit.getScheduler().runTaskTimer(this, this::expireCamps, expiry, expiry);
    }

    private void stopTasks() {
        if (autosaveTask != null) autosaveTask.cancel();
        if (expiryTask != null) expiryTask.cancel();
        autosaveTask = null;
        expiryTask = null;
    }

    private void expireCamps() {
        long now = System.currentTimeMillis();
        for (Camp camp : new ArrayList<>(repository.all())) {
            if (camp.burnUntil() > now && camp.remainingLifeMillis(now, fuel.maximumLifeMillis()) > 0) continue;
            Player owner = Bukkit.getPlayer(camp.ownerId());
            camps.pack(camp, owner, true);
        }
    }

    private void copyResource(String name) {
        if (!new File(getDataFolder(), name).exists()) saveResource(name, false);
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor, org.bukkit.command.TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) throw new IllegalStateException("Команда /" + name + " отсутствует в plugin.yml");
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    /**
     * Стабильная точка интеграции для аддонов NeverLand.
     * Репозиторий остаётся владельцем живых объектов лагерей; вызывающий плагин не должен их кэшировать.
     */
    public CampRepository repository() {
        return repository;
    }
}
