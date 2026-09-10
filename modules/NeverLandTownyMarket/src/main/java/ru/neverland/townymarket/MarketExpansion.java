package ru.neverland.townymarket;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import com.palmergames.bukkit.towny.TownyAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
public final class MarketExpansion extends PlaceholderExpansion {
    private final NeverLandTownyMarket plugin;private final MarketService service;
    public MarketExpansion(NeverLandTownyMarket plugin,MarketService service){this.plugin=plugin;this.service=service;}
    public String getIdentifier(){return "nltmarket";}public String getAuthor(){return "Alexander Sokolov";}public String getVersion(){return plugin.getPluginMeta().getVersion();}public boolean persist(){return true;}
    public String onRequest(OfflinePlayer player,String key){if(player==null)return "";var stats=service.stats(player.getUniqueId());
        return switch(key.toLowerCase(java.util.Locale.ROOT)){case "offers"->Long.toString(stats.offers());case "pickup"->Long.toString(stats.pickup());default->null;};}
}
