package ru.neverland.townyresearch.integration;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import ru.neverland.townyresearch.api.TownyResearchApi;
import ru.neverland.townyresearch.service.Ui;
import java.util.Locale;
public final class ResearchExpansion extends PlaceholderExpansion {
    private final TownyResearchApi service;private final String version;
    public ResearchExpansion(TownyResearchApi service,String version){this.service=service;this.version=version;}
    @Override public String getIdentifier(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return "nltresearch";} }@Override public String getAuthor(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return "Alexander Sokolov";} }@Override public String getVersion(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return version;} }@Override public boolean persist(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return true;} }
    @Override public String onRequest(OfflinePlayer player,String parameters){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {if(player==null)return "";var s=service.residentResearch(player.getUniqueId()).orElse(null);if(s==null)return "";String key=parameters.toLowerCase(Locale.ROOT);if(key.startsWith("level_"))return String.valueOf(s.state().learned().getOrDefault(key.substring(6),0));if(key.startsWith("bonus_"))return Ui.percent(service.bonus(s.townId(),key.substring(6)));var active=s.state().active();
        return switch(key){case "knowledge"->Ui.amount(s.knowledge());case "reserved"->Ui.amount(active==null||active.phase()==ru.neverland.townyresearch.model.CityStudy.Phase.PREPARED?0:active.cost());case "completed"->String.valueOf(s.state().learned().values().stream().mapToInt(Integer::intValue).sum());case "active"->active==null?"нет":service.name(active.technology());case "remaining"->String.valueOf(active==null?0:active.remaining());case "status"->s.status();case "paused"->s.paused()?"да":"нет";default->null;};
    } }
}
