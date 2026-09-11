package ru.neverland.integration;
import org.bukkit.Bukkit;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Runtime lookup avoids a loading cycle: Districts depends on Builds. */
public final class DistrictBonuses {
    private DistrictBonuses(){}
    public static double multiplier(UUID town,String project){
        try{var c=ru.neverland.core.ApiServices.connect("NeverLandTownyDistricts","ru.neverland.townydistricts.api.TownyDistrictsApi",1,"multiplier");if(!c.ready())return 1;
            double value=((Number)c.invoke("multiplier",new Class<?>[]{UUID.class,String.class},town,project)).doubleValue();
            return Double.isFinite(value)?Math.max(1,Math.min(3,value)):1;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return 1;}
    }
    public static int output(int base,double multiplier){
        double amount=base*Math.max(1,Math.min(3,multiplier));int result=(int)amount;
        return result+(ThreadLocalRandom.current().nextDouble()<amount-result?1:0);
    }
}
