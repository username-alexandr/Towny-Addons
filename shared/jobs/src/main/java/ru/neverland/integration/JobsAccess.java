package ru.neverland.integration;
import org.bukkit.Bukkit;
import java.util.UUID;
/** Runtime lookup avoids a Builds -> Jobs -> Builds load cycle. Missing Jobs adds no bonus. */
public final class JobsAccess {
    private JobsAccess(){}
    private static Object query(String method,UUID town,String id)throws ReflectiveOperationException{
        if(!Bukkit.isPrimaryThread())return null;var plugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyJobs");if(plugin==null||!plugin.isEnabled())return null;
        Class<?> api=Class.forName("ru.neverland.townyjobs.api.TownyJobsApi",true,plugin.getClass().getClassLoader());Object provider=Bukkit.getServicesManager().load(api);if(provider==null)return null;
        return method.equals("supports")?api.getMethod(method,String.class).invoke(provider,id):api.getMethod(method,UUID.class,String.class).invoke(provider,town,id);
    }
    private static double value(String method,UUID town,String id){if(town==null||id==null)return 0;try{Object v=query(method,town,id);return v instanceof Number n?JobsEffects.bonus(n.doubleValue()):0;}catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return 0;}}
    public static double bonus(UUID town,String project){return value("bonus",town,project);}
    public static double townBonus(UUID town,String effect){return value("townBonus",town,effect);}
    public static double multiplier(UUID town,String project,double current){return JobsEffects.multiplier(current,bonus(town,project));}
    public static int workers(UUID town,String project){try{Object v=query("workers",town,project);return v instanceof Number n?Math.max(0,Math.min(20,n.intValue())):0;}catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return 0;}}
    public static boolean supports(String project){try{return Boolean.TRUE.equals(query("supports",null,project));}catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return false;}}
}
