package ru.neverland.townymarket;
import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
public final class NeverLandTownyMarket extends JavaPlugin {
    private MarketService service;private MarketMenus menus;private boolean registered;
    @Override public void onEnable(){saveDefaultConfig();if(!getDataFolder().toPath().resolve("catalog.yml").toFile().exists())saveResource("catalog.yml",false);
        service=new MarketService(this);menus=new MarketMenus(this,service);var commands=new MarketCommands(this,service,menus);
        getServer().getPluginManager().registerEvents(menus,this);var command=getCommand("townymarket");if(command!=null){command.setExecutor(commands);command.setTabCompleter(commands);}
        if(!(registered=TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"market",commands)))getLogger().severe("Подкоманда /t market занята; используйте /townymarket");
        getServer().getServicesManager().register(MarketApi.class,new MarketApi(service),this,ServicePriority.Normal);service.start();
        if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))new MarketExpansion(this,service).register();
        getLogger().info("Городской и международный рынки включены. Журнал: "+service.available());
    }
    @Override public void onDisable(){if(menus!=null)menus.stop();if(service!=null)service.stop();if(registered)TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"market");getServer().getServicesManager().unregisterAll(this);}
}
