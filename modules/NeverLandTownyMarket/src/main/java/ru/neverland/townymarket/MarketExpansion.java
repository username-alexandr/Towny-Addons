package ru.neverland.townymarket;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import com.palmergames.bukkit.towny.TownyAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
public final class MarketExpansion extends PlaceholderExpansion {
    private final NeverLandTownyMarket plugin;private final MarketService service;
    public MarketExpansion(NeverLandTownyMarket plugin,MarketService service){this.plugin=plugin;this.service=service;}
    public String getIdentifier(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return "nltmarket";} }public String getAuthor(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return "Alexander Sokolov";} }public String getVersion(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return plugin.getPluginMeta().getVersion();} }public boolean persist(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return true;} }
    public String onRequest(OfflinePlayer player,String key){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {if(player==null)return "";var stats=service.stats(player.getUniqueId());
        return switch(key.toLowerCase(java.util.Locale.ROOT)){case "offers"->Long.toString(stats.offers());case "pickup"->Long.toString(stats.pickup());default->null;};} }
}
