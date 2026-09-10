package ru.neverland.integration;
import org.bukkit.Bukkit;
import java.util.UUID;
public final class SpecializationAccess {
    private SpecializationAccess(){}
    private static Object query(String method,UUID town,String id)throws ReflectiveOperationException{
        var plugin=Bukkit.getPluginManager().getPlugin("NeverLandTownySpecialization");if(plugin==null||!plugin.isEnabled())return null;
        Class<?> api=Class.forName("ru.neverland.townyspecialization.api.TownySpecializationApi",true,plugin.getClass().getClassLoader());Object provider=Bukkit.getServicesManager().load(api);return provider==null?null:api.getMethod(method,UUID.class,String.class).invoke(provider,town,id);
    }
    public static boolean allowed(UUID town,String project){if(SpecializationRules.required(project).isEmpty())return true;if(town==null)return false;try{return Boolean.TRUE.equals(query("canUseBuilding",town,project));}catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return false;}}
    public static double bonus(UUID town,String effect){if(town==null||!SpecializationRules.EFFECTS.contains(effect))return 0;try{Object value=query("bonus",town,effect);return value instanceof Number n?SpecializationRules.bound(effect,n.doubleValue()):0;}catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return 0;}}
    public static double production(UUID town,String project,double current){try{Object value=query("productionBonus",town,project);return SpecializationRules.production(current,value instanceof Number n?n.doubleValue():0);}catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return current;}}
    public static String requirement(String project){return switch(SpecializationRules.required(project)){case "trade"->"Торговый центр";case "fortress"->"Крепость";case "agricultural"->"Аграрный центр";case "industrial"->"Промышленный город";case "scientific"->"Научный центр";case "port"->"Портовый город";case "religious"->"Религиозный центр";default->"";};}
    public static String reason(String project){return "Нужна специализация «"+requirement(project)+"»: /t specialization";}
}
