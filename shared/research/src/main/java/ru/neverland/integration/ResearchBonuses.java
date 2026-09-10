package ru.neverland.integration;
import org.bukkit.Bukkit;
import java.util.UUID;
/** Read committed snapshots only. Research depends on Resources/Builds, never the reverse. */
public final class ResearchBonuses {
    private ResearchBonuses(){}
    public static double bonus(UUID town,String technology){if(town==null||technology==null||technology.isEmpty())return 0;var plugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyResearch");if(plugin==null||!plugin.isEnabled())return 0;
        try{Class<?> api=Class.forName("ru.neverland.townyresearch.api.TownyResearchApi",true,plugin.getClass().getClassLoader());Object service=Bukkit.getServicesManager().load(api);if(service==null)return 0;return ResearchEffects.bounded(((Number)api.getMethod("bonus",UUID.class,String.class).invoke(service,town,technology)).doubleValue());}catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return 0;}}
    public static double production(UUID town,String project){return production(town,project,1);}
    public static double production(UUID town,String project,double district){return ResearchEffects.production(district,bonus(town,ResearchEffects.productionTechnology(project)));}
}
