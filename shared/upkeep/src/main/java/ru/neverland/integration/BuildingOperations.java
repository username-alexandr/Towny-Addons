package ru.neverland.integration;
import org.bukkit.Bukkit;
import java.util.UUID;
/** Resolve at use time to avoid circular plugin dependencies. Power reads maintained(), never active(). */
public final class BuildingOperations {
    private BuildingOperations(){}
    private static boolean query(UUID town,String project,String pluginName,String apiName,String method){
        var plugin=Bukkit.getPluginManager().getPlugin(pluginName);if(plugin==null)return true;if(!plugin.isEnabled())return false;
        try{Class<?> api=Class.forName(apiName,true,plugin.getClass().getClassLoader());Object service=Bukkit.getServicesManager().load(api);return service!=null&&Boolean.TRUE.equals(api.getMethod(method,UUID.class,String.class).invoke(service,town,project));}
        catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return false;}
    }
    public static boolean maintained(UUID town,String project){return query(town,project,"NeverLandTownyUpkeep","ru.neverland.townyupkeep.api.TownyUpkeepApi","active");}
    public static boolean powered(UUID town,String project){return query(town,project,"NeverLandTownyPower","ru.neverland.townypower.api.TownyPowerApi","powered");}
    public static boolean active(UUID town,String project){return SpecializationAccess.allowed(town,project)&&maintained(town,project)&&powered(town,project);}
    public static String inactiveReason(UUID town,String project){
        if(!SpecializationAccess.allowed(town,project))return SpecializationAccess.reason(project);
        if(!maintained(town,project))return "Содержание не оплачено или недоступно: /t upkeep";
        if(powered(town,project))return "Здание работает";
        var plugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyPower");
        try{if(plugin!=null&&plugin.isEnabled()){Class<?> api=Class.forName("ru.neverland.townypower.api.TownyPowerApi",true,plugin.getClass().getClassLoader());Object service=Bukkit.getServicesManager().load(api);if(service!=null)return api.getMethod("status",UUID.class,String.class).invoke(service,town,project)+": /t power";}}
        catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){}
        return "Энергосеть недоступна: /t power";
    }
    public static int level(UUID town,String project,int builtLevel){return builtLevel>0&&active(town,project)?builtLevel:0;}
}
