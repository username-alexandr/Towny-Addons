package ru.neverland.townycrime;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import com.palmergames.bukkit.towny.TownyAPI;
public final class CrimeExpansion extends PlaceholderExpansion {
    private final CrimeService service;private final String version;
    public CrimeExpansion(CrimeService s,String v){service=s;version=v;}
    @Override public String getIdentifier(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return "townycrime";} }@Override public String getAuthor(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return "Alexander Sokolov";} }@Override public String getVersion(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return version;} }@Override public boolean persist(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return true;} }
    @Override public String onRequest(OfflinePlayer p,String key){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {if(p==null||!ru.neverland.core.SuiteState.held())return "—";var resident=TownyAPI.getInstance().getResident(p.getUniqueId());if(resident==null||!resident.hasTown())return "—";var snapshot=service.crime(resident.getTownOrNull().getUUID()).orElse(null);if(snapshot==null)return "—";if(key.equals("status"))return (String)snapshot.get("status");if(Boolean.TRUE.equals(snapshot.get("paused")))return "—";return switch(key){case "level","happiness","guard"->CrimeMenu.number((Double)snapshot.get(key));case "loss"->CrimeMenu.number((10000-(Integer)snapshot.get("incomeBasisPoints"))/100.0);default->null;};} }
}
