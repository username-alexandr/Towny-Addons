package ru.neverland.integration;
import org.bukkit.Bukkit;
import java.util.UUID;
/** Resolve at use time to avoid circular plugin dependencies. Power reads maintained(), never active(). */
public final class BuildingOperations {
    private BuildingOperations(){}
    private static boolean query(UUID town,String project,String pluginName,String apiName,String method){
        try{var c=ru.neverland.core.ApiServices.connect(pluginName,apiName,1,method);if(c.state()==ru.neverland.core.ApiServices.State.NOT_INSTALLED)return true;return c.ready()&&Boolean.TRUE.equals(c.invoke(method,new Class<?>[]{UUID.class,String.class},town,project));}
        catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return false;}
    }
    public static boolean maintained(UUID town,String project){return query(town,project,"NeverLandTownyUpkeep","ru.neverland.townyupkeep.api.TownyUpkeepApi","active");}
    public static boolean powered(UUID town,String project){return query(town,project,"NeverLandTownyPower","ru.neverland.townypower.api.TownyPowerApi","powered");}
    public static boolean active(UUID town,String project){return SpecializationAccess.allowed(town,project)&&maintained(town,project)&&powered(town,project);}
    public static String inactiveReason(UUID town,String project){
        if(!SpecializationAccess.allowed(town,project))return SpecializationAccess.reason(project);
        if(!maintained(town,project))return "Содержание не оплачено или недоступно: /t upkeep";
        if(powered(town,project))return "Здание работает";
        try{var c=ru.neverland.core.ApiServices.connect("NeverLandTownyPower","ru.neverland.townypower.api.TownyPowerApi",1,"status");if(c.ready())return c.invoke("status",new Class<?>[]{UUID.class,String.class},town,project)+": /t power";}
        catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){}
        return "Энергосеть недоступна: /t power";
    }
    public static int level(UUID town,String project,int builtLevel){return builtLevel>0&&active(town,project)?builtLevel:0;}
}
