package ru.neverland.townyjustice;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
public final class JusticeExpansion extends PlaceholderExpansion {
    private final JusticeService service;private final String version;
    public JusticeExpansion(JusticeService s,String v){service=s;version=v;}
    @Override public String getIdentifier(){return "townyjustice";}@Override public String getAuthor(){return "Alexander Sokolov";}@Override public String getVersion(){return version;}@Override public boolean persist(){return true;}
    @Override public String onRequest(OfflinePlayer p,String key){if(p==null||!Bukkit.isPrimaryThread()||!service.healthy())return "—";return switch(key){case "fines"->Integer.toString(service.fines(p.getUniqueId()).size());case "fine_total"->JusticeMenu.money(service.fines(p.getUniqueId()).stream().mapToLong(c->(Long)c.get("amount")).sum());case "wanted"->Integer.toString(service.wanted(p.getUniqueId()).size());default->null;};}
}
