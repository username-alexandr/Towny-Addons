package ru.neverland.townymarket;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import com.palmergames.bukkit.towny.TownyAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
public final class MarketExpansion extends PlaceholderExpansion {
    private final NeverLandTownyMarket plugin;private final MarketService service;
    public MarketExpansion(NeverLandTownyMarket plugin,MarketService service){this.plugin=plugin;this.service=service;}
    public String getIdentifier(){ synchronized (ru.neverland.core.SuiteState.lock()) {return "nltmarket";} }public String getAuthor(){ synchronized (ru.neverland.core.SuiteState.lock()) {return "Alexander Sokolov";} }public String getVersion(){ synchronized (ru.neverland.core.SuiteState.lock()) {return plugin.getPluginMeta().getVersion();} }public boolean persist(){ synchronized (ru.neverland.core.SuiteState.lock()) {return true;} }
    public String onRequest(OfflinePlayer player,String key){ synchronized (ru.neverland.core.SuiteState.lock()) {if(player==null)return "";var stats=service.stats(player.getUniqueId());
        return switch(key.toLowerCase(java.util.Locale.ROOT)){case "offers"->Long.toString(stats.offers());case "pickup"->Long.toString(stats.pickup());default->null;};} }
}
