package ru.neverland.townypower.integration;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import ru.neverland.townypower.api.TownyPowerApi;
import java.util.Locale;
public final class PowerExpansion extends PlaceholderExpansion {
    private final TownyPowerApi service;private final String version;
    public PowerExpansion(TownyPowerApi service,String version){this.service=service;this.version=version;}
    @Override public String getIdentifier(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return "nltpower";} }
    @Override public String getAuthor(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return "Alexander Sokolov";} }
    @Override public String getVersion(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return version;} }
    @Override public boolean persist(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return true;} }
    @Override public String onRequest(OfflinePlayer player,String parameters){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {if(player==null)return "";var s=service.residentPower(player.getUniqueId()).orElse(null);if(s==null)return "";String key=parameters.toLowerCase(Locale.ROOT);
        if(key.startsWith("building_")){if(s.paused())return "Расчёт сети недоступен";var a=s.grid().buildings().get(key.substring(9));return a==null?"":a.status().title;}
        return switch(key){case "generation"->String.valueOf(s.grid().generation());case "demand"->String.valueOf(s.grid().demand());case "supplied"->String.valueOf(s.grid().supplied());case "spare"->String.valueOf(s.grid().spare());case "deficit"->String.valueOf(s.grid().deficit());case "paused"->s.paused()?"да":"нет";case "status"->s.status();default->null;};
    } }
}
