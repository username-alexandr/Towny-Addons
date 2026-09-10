package ru.neverland.integration;
import java.util.*;
import org.bukkit.Bukkit;
/** Optional service lookup; unavailable import controls cannot silently open a border. */
public final class PoliciesAccess {
    private PoliciesAccess(){}
    private static Object call(String method,Class<?>[] signature,Object... values){try{var plugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyPolicies");if(plugin==null||!plugin.isEnabled())return null;Class<?> type=Class.forName("ru.neverland.townypolicies.api.TownyPoliciesApi",true,plugin.getClass().getClassLoader());Object api=Bukkit.getServicesManager().load(type);return api==null?null:type.getMethod(method,signature).invoke(api,values);}catch(Exception|LinkageError e){return null;}}
    public static double effect(UUID town,String key){Object v=call("effect",new Class<?>[]{UUID.class,String.class},town,key);return v instanceof Number n?PolicyEffects.bound(n.doubleValue(),key.equals("happiness")?-40:-.5,key.equals("happiness")?40:.5,0):0;}
    private static double multiplier(String method,UUID town,String project){Object v=call(method,new Class<?>[]{UUID.class,String.class},town,project);return v instanceof Number n?PolicyEffects.bound(n.doubleValue(),method.equals("upkeepMultiplier")?1:.25,method.equals("upkeepMultiplier")?3:2,1):1;}
    public static double production(UUID town,String project){return multiplier("productionMultiplier",town,project);}
    public static double upkeep(UUID town,String project){return multiplier("upkeepMultiplier",town,project);}
    public static double tax(UUID town){Object v=call("taxMultiplier",new Class<?>[]{UUID.class},town);return v instanceof Number n?PolicyEffects.bound(n.doubleValue(),.5,1.5,1):1;}
    public static double tariff(UUID town,double manual,double maximum){Object v=call("tariff",new Class<?>[]{UUID.class,double.class,double.class},town,manual,maximum);return v instanceof Number n?PolicyEffects.bound(n.doubleValue(),0,PolicyEffects.bound(maximum,0,100,20),manual):manual;}
    public static boolean tariffManaged(UUID town){return Boolean.TRUE.equals(call("tariffManaged",new Class<?>[]{UUID.class},town));}
    public static boolean importsAllowed(UUID buyer,UUID seller,boolean sameNation){try{if(Bukkit.getPluginManager().getPlugin("NeverLandTownyPolicies")==null)return true;return Boolean.TRUE.equals(call("importsAllowed",new Class<?>[]{UUID.class,UUID.class,boolean.class},buyer,seller,sameNation));}catch(Exception|LinkageError e){return false;}}
}
