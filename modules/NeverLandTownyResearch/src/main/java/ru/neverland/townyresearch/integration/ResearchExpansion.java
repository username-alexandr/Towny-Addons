package ru.neverland.townyresearch.integration;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import ru.neverland.townyresearch.api.TownyResearchApi;
import ru.neverland.townyresearch.service.Ui;
import java.util.Locale;
public final class ResearchExpansion extends PlaceholderExpansion {
    private final TownyResearchApi service;private final String version;
    public ResearchExpansion(TownyResearchApi service,String version){this.service=service;this.version=version;}
    @Override public String getIdentifier(){return "nltresearch";}@Override public String getAuthor(){return "Alexander Sokolov";}@Override public String getVersion(){return version;}@Override public boolean persist(){return true;}
    @Override public String onRequest(OfflinePlayer player,String parameters){if(player==null)return "";var s=service.residentResearch(player.getUniqueId()).orElse(null);if(s==null)return "";String key=parameters.toLowerCase(Locale.ROOT);if(key.startsWith("level_"))return String.valueOf(s.state().learned().getOrDefault(key.substring(6),0));if(key.startsWith("bonus_"))return Ui.percent(service.bonus(s.townId(),key.substring(6)));var active=s.state().active();
        return switch(key){case "knowledge"->Ui.amount(s.knowledge());case "reserved"->Ui.amount(active==null||active.phase()==ru.neverland.townyresearch.model.CityStudy.Phase.PREPARED?0:active.cost());case "completed"->String.valueOf(s.state().learned().values().stream().mapToInt(Integer::intValue).sum());case "active"->active==null?"нет":service.name(active.technology());case "remaining"->String.valueOf(active==null?0:active.remaining());case "status"->s.status();case "paused"->s.paused()?"да":"нет";default->null;};
    }
}
