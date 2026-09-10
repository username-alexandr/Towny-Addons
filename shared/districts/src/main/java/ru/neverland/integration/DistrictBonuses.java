package ru.neverland.integration;
import org.bukkit.Bukkit;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Runtime lookup avoids a loading cycle: Districts depends on Builds. */
public final class DistrictBonuses {
    private DistrictBonuses(){}
    public static double multiplier(UUID town,String project){
        var plugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyDistricts");
        if(plugin==null||!plugin.isEnabled())return 1;
        try{
            Class<?> type=Class.forName("ru.neverland.townydistricts.api.TownyDistrictsApi",true,plugin.getClass().getClassLoader());
            Object provider=Bukkit.getServicesManager().load(type);if(provider==null)return 1;
            double value=((Number)type.getMethod("multiplier",UUID.class,String.class).invoke(provider,town,project)).doubleValue();
            return Double.isFinite(value)?Math.max(1,Math.min(3,value)):1;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return 1;}
    }
    public static int output(int base,double multiplier){
        double amount=base*Math.max(1,Math.min(3,multiplier));int result=(int)amount;
        return result+(ThreadLocalRandom.current().nextDouble()<amount-result?1:0);
    }
}
