package ru.neverland.integration;
import org.bukkit.Bukkit;
import java.util.UUID;
/** Read committed snapshots only. Research depends on Resources/Builds, never the reverse. */
public final class ResearchBonuses {
    private ResearchBonuses(){}
    public static double bonus(UUID town,String technology){if(town==null||technology==null||technology.isEmpty())return 0;try{var c=ru.neverland.core.ApiServices.connect("NeverLandTownyResearch","ru.neverland.townyresearch.api.TownyResearchApi",1,"bonus");return c.ready()?ResearchEffects.bounded(((Number)c.invoke("bonus",new Class<?>[]{UUID.class,String.class},town,technology)).doubleValue()):0;}catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return 0;}}
    public static double production(UUID town,String project){return production(town,project,1);}
    public static double production(UUID town,String project,double district){return ResearchEffects.production(district,bonus(town,ResearchEffects.productionTechnology(project)));}
}
