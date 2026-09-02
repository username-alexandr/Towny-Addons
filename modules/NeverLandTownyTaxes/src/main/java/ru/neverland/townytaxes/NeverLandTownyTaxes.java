package ru.neverland.townytaxes;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townytaxes.api.NeverLandTownyTaxesApi;
import ru.neverland.townytaxes.command.*;
import ru.neverland.townytaxes.data.CivicRepository;
import ru.neverland.townytaxes.integration.TownyHook;
import ru.neverland.townytaxes.integration.PlaceholderHook;
import ru.neverland.townytaxes.listener.EconomyListener;
import ru.neverland.townytaxes.listener.RestrictionListener;
import ru.neverland.townytaxes.service.FiscalService;
import ru.neverland.townytaxes.service.MessageService;

import java.io.File;
import java.util.List;
import java.util.UUID;

public final class NeverLandTownyTaxes extends JavaPlugin {
    private static final List<String> TOWN_COMMANDS=List.of("taxes","tax","sanctions","agreements","deals");private static final List<String>NATION_COMMANDS=List.of("taxes","tax","sanctions","agreements","deals");
    private TownyHook towny;private MessageService messages;private CivicRepository repository;private FiscalService fiscal;
    @Override public void onEnable(){saveDefaultConfig();copy("messages.yml");towny=new TownyHook(this);messages=new MessageService(this);repository=new CivicRepository(this);repository.load();fiscal=new FiscalService(this,towny,repository,messages);
        TownTaxesCommand taxes=new TownTaxesCommand(towny,fiscal,messages);TownSanctionsCommand sanctions=new TownSanctionsCommand(towny,fiscal,messages);TownAgreementsCommand agreements=new TownAgreementsCommand(towny,fiscal,messages);
        register("taxes",taxes);register("tax",taxes);register("sanctions",sanctions);register("agreements",agreements);register("deals",agreements);
        registerNation("taxes",new NationEconomyCommand(NationEconomyCommand.View.TAXES,towny,fiscal,messages));registerNation("tax",new NationEconomyCommand(NationEconomyCommand.View.TAXES,towny,fiscal,messages));registerNation("sanctions",new NationEconomyCommand(NationEconomyCommand.View.SANCTIONS,towny,fiscal,messages));registerNation("agreements",new NationEconomyCommand(NationEconomyCommand.View.AGREEMENTS,towny,fiscal,messages));registerNation("deals",new NationEconomyCommand(NationEconomyCommand.View.AGREEMENTS,towny,fiscal,messages));
        PluginCommand admin=getCommand("townytaxes");if(admin!=null){AdminCommand executor=new AdminCommand(this,towny,fiscal,messages);admin.setExecutor(executor);admin.setTabCompleter(executor);}
        getServer().getPluginManager().registerEvents(new EconomyListener(fiscal),this);getServer().getPluginManager().registerEvents(new RestrictionListener(towny,fiscal,messages),this);
        getServer().getServicesManager().register(NeverLandTownyTaxesApi.class,fiscal,this,ServicePriority.Normal);boolean papi=PlaceholderHook.register(this,towny,fiscal);fiscal.start();long autosave=Math.max(20,getConfig().getLong("scheduler.autosave-seconds",60)*20);getServer().getScheduler().runTaskTimer(this,repository::saveIfDirty,autosave,autosave);
        getLogger().info("NeverLandTownyTaxes 0.1.0 включён: налоговых политик "+repository.policies().size()+", санкций "+repository.sanctions().size()+", соглашений "+repository.agreements().size()+", PlaceholderAPI="+papi+".");}
    @Override public void onDisable(){if(fiscal!=null)fiscal.shutdown();if(towny!=null){for(String name:TOWN_COMMANDS)towny.unregister(name);for(String name:NATION_COMMANDS)towny.unregisterNation(name);}getServer().getServicesManager().unregisterAll(this);}
    public void reloadPlugin(){reloadConfig();messages.reload();fiscal.start();}
    public boolean isTradeBlocked(UUID firstTown,UUID secondTown){return fiscal!=null&&fiscal.isTradeBlocked(firstTown,secondTown);}public double tradePreferenceMultiplier(UUID firstTown,UUID secondTown){return fiscal==null?1:fiscal.tradePreferenceMultiplier(firstTown,secondTown);}
    private void register(String name,org.bukkit.command.CommandExecutor executor){if(!towny.register(name,executor))getLogger().severe("Не удалось зарегистрировать /t "+name+".");}
    private void registerNation(String name,org.bukkit.command.CommandExecutor executor){if(!towny.registerNation(name,executor))getLogger().severe("Не удалось зарегистрировать /n "+name+".");}
    private void copy(String name){if(!new File(getDataFolder(),name).exists())saveResource(name,false);}
}
