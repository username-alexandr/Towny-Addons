package ru.neverland.morstownstick;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.morstownstick.command.AdminCommand;
import ru.neverland.morstownstick.command.StickCommand;
import ru.neverland.morstownstick.integration.TownyClaimAdapter;
import ru.neverland.morstownstick.integration.TownyFacade;
import ru.neverland.morstownstick.integration.TownCommandInterceptor;
import ru.neverland.morstownstick.listener.ClaimCommandListener;
import ru.neverland.morstownstick.listener.CraftProtectionListener;
import ru.neverland.morstownstick.listener.StickListener;
import ru.neverland.morstownstick.listener.TownBorderListener;
import ru.neverland.morstownstick.service.BorderCache;
import ru.neverland.morstownstick.service.ClaimService;
import ru.neverland.morstownstick.service.MessageService;
import ru.neverland.morstownstick.service.ParticleService;
import ru.neverland.morstownstick.service.SelectionService;
import ru.neverland.morstownstick.service.StickService;

public final class MORSTownStick extends JavaPlugin {
    private TownyFacade towny;
    private SelectionService selections;
    private BorderCache borders;
    private ParticleService particles;
    private ClaimService claims;
    private TownCommandInterceptor commandInterceptor;

    @Override
    public void onEnable() {
        ru.neverland.morstownstick.util.LegacyDataMigrator.migrate(this, "MORSTownStick");
        saveDefaultConfig();

        MessageService messages = new MessageService(this);
        towny = new TownyFacade();
        selections = new SelectionService(this);
        StickService sticks = new StickService(this);
        borders = new BorderCache(this, towny);
        TownyClaimAdapter claimAdapter;
        try {
            claimAdapter = new TownyClaimAdapter(this);
        } catch (IllegalStateException exception) {
            getLogger().severe("Несовместимая версия Towny: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        claims = new ClaimService(this, towny, claimAdapter, selections, messages, borders);
        particles = new ParticleService(this, selections, towny, borders);

        StickCommand stickCommand = new StickCommand(towny, sticks, selections, messages, claims);
        if (!towny.registerStickCommand(stickCommand)) {
            getLogger().severe("Не удалось зарегистрировать /t stick: команда уже занята другим аддоном.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        PluginCommand admin = getCommand("townystick");
        if (admin == null) throw new IllegalStateException("Command townstick is missing from plugin.yml");
        admin.setExecutor(new AdminCommand(this, messages, sticks));

        PluginManager manager = getServer().getPluginManager();
        manager.registerEvents(new StickListener(this, towny, sticks, selections, claims, messages), this);
        commandInterceptor = new TownCommandInterceptor(this, selections, claims, stickCommand);
        if (!commandInterceptor.install()) {
            getLogger().warning("Прямая обёртка /town недоступна; используется резервный перехват команды.");
            manager.registerEvents(new ClaimCommandListener(selections, claims), this);
        }
        manager.registerEvents(new CraftProtectionListener(sticks), this);
        manager.registerEvents(new TownBorderListener(borders), this);
        particles.start();
        getLogger().info("NeverLandTownyStick 0.1.6 включён: список выделения, BFS-захват и защита палки активны.");
    }

    public void reloadPlugin() {
        reloadConfig();
        borders.clear();
        particles.start();
    }

    @Override
    public void onDisable() {
        if (commandInterceptor != null) commandInterceptor.uninstall();
        if (particles != null) particles.stop();
        if (claims != null) claims.shutdown();
        if (selections != null) selections.clearAll();
        if (towny != null) towny.unregisterStickCommand();
    }
}
