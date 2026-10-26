package ru.neverland.townyresources.integration;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import ru.neverland.townyresources.api.TownyResourcesApi;
import ru.neverland.townyresources.model.*;
import java.util.Locale;
public final class ResourcesExpansion extends PlaceholderExpansion {
    private final TownyResourcesApi api;private final String version;
    public ResourcesExpansion(TownyResourcesApi api,String version){this.api=api;this.version=version;}
    @Override public String getIdentifier(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return "nltresources";} }
    @Override public String getAuthor(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return "Alexander Sokolov";} }
    @Override public String getVersion(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return version;} }
    @Override public boolean persist(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return true;} }
    @Override public String onRequest(OfflinePlayer player,String parameters){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        if(player==null)return "";var s=api.residentResources(player.getUniqueId()).orElse(null);if(s==null)return "";String key=parameters.toLowerCase(Locale.ROOT);
        if(key.equals("paused"))return s.paused()?"да":"нет";
        if(key.equals("food_coverage"))return String.format(Locale.ROOT,"%.1f",s.foodCoverage()*100);
        if(key.equals("water_coverage"))return String.format(Locale.ROOT,"%.1f",s.waterCoverage()*100);
        for(var r:Resource.values()){
            if(key.equals(r.id()))return Amounts.decimal(s.state().balances().get(r));
            if(key.equals(r.id()+"_capacity"))return Amounts.decimal(s.capacity().get(r));
            if(key.equals(r.id()+"_income"))return Amounts.decimal(s.forecastIncome().get(r));
            if(key.equals(r.id()+"_expense"))return Amounts.decimal(s.forecastExpense().get(r));
            if(key.equals(r.id()+"_net"))return Amounts.decimal(s.forecastIncome().get(r)-s.forecastExpense().get(r));
        }
        return null;
    } }
}
