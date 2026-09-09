package ru.neverland.townypopulation.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import ru.neverland.townypopulation.api.TownyPopulationApi;
import java.util.Locale;

public final class PopulationExpansion extends PlaceholderExpansion {
    private final TownyPopulationApi service;
    private final String version;
    public PopulationExpansion(TownyPopulationApi service, String version) { this.service=service; this.version=version; }
    @Override public String getIdentifier() { return "nltpopulation"; }
    @Override public String getAuthor() { return "Alexander Sokolov"; }
    @Override public String getVersion() { return version; }
    @Override public boolean persist() { return true; }
    @Override public String onRequest(OfflinePlayer player, String parameters) {
        if(player==null) return "";
        var s=service.residentPopulation(player.getUniqueId()).orElse(null);
        if(s==null) return "";
        return switch(parameters.toLowerCase(Locale.ROOT)) {
            case "population" -> String.valueOf(s.population());
            case "housing" -> String.valueOf(s.capacity().housing());
            case "jobs" -> String.valueOf(s.capacity().jobs());
            case "employed" -> String.valueOf(s.metrics().employed());
            case "unemployed" -> String.valueOf(s.metrics().unemployed());
            case "unemployment" -> number(s.metrics().unemployment()*100);
            case "happiness" -> number(s.metrics().happiness());
            case "food" -> number(s.metrics().foodCoverage()*100);
            case "water" -> number(s.metrics().waterCoverage()*100);
            case "growth" -> number(s.paused()?0:s.metrics().change());
            case "last_change" -> String.valueOf(s.lastChange());
            case "paused" -> s.paused()?"да":"нет";
            default -> null;
        };
    }
    private static String number(double n) { return String.format(Locale.ROOT,"%.1f",n); }
}
