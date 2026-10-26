package ru.neverland.townyjustice;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
public final class JusticeExpansion extends PlaceholderExpansion {
    private final JusticeService service;private final String version;
    public JusticeExpansion(JusticeService s,String v){service=s;version=v;}
    @Override public String getIdentifier(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return "townyjustice";} }@Override public String getAuthor(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return "Alexander Sokolov";} }@Override public String getVersion(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return version;} }@Override public boolean persist(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return true;} }
    @Override public String onRequest(OfflinePlayer p,String key){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {if(p==null||!ru.neverland.core.SuiteState.held()||!service.healthy())return "—";return switch(key){case "fines"->Integer.toString(service.fines(p.getUniqueId()).size());case "fine_total"->JusticeMenu.money(service.fines(p.getUniqueId()).stream().mapToLong(c->(Long)c.get("amount")).sum());case "wanted"->Integer.toString(service.wanted(p.getUniqueId()).size());default->null;};} }
}
