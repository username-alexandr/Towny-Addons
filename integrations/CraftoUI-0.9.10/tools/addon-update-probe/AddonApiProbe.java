package cc.crafto.ui.server;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Actual Builds JAR and production UI schema adapter. No game server, stubs, players or world reads. */
public final class AddonApiProbe {
    private static int checks;
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    public static void main(String[] args)throws Exception{
        Class<?> styles=Class.forName("ru.neverland.townybuilds.construction.BuildingStyle");
        Class<?> sites=Class.forName("ru.neverland.townybuilds.construction.ConstructionSite");
        int current=sites.getField("CURRENT_ARCHITECTURE_VERSION").getInt(null);
        var yaml=new YamlConfiguration();
        for(int prefix:new int[]{0,1000,2000})for(int base=1;base<=current;base++){
            int saved=prefix+base;yaml.set("architecture-version",saved);
            check(CityBuildSchema.architecture(yaml,current,styles)==saved,"saved styled architecture preserved: "+saved);
        }
        for(int invalid:new int[]{0,1000,2000,3006,1000+current+1,2000+current+1}){
            yaml.set("architecture-version",invalid);boolean refused=false;
            try{CityBuildSchema.architecture(yaml,current,styles);}catch(IllegalArgumentException expected){refused=true;}
            check(refused,"unknown style/version must be refused: "+invalid);
        }
        String pin="a".repeat(64);yaml.set("blueprint-set",pin);
        for(int prefix:new int[]{0,1000,2000})for(int base=1;base<=current;base++){
            int saved=10000+prefix+base;yaml.set("architecture-version",saved);
            check(CityBuildSchema.architecture(yaml,current,styles)==saved,"pinned styled architecture preserved: "+saved);
        }
        yaml.set("blueprint-set",null);
        Class<?> api=Class.forName("ru.neverland.townybuilds.api.BuildingPlanningApi");
        check(java.util.concurrent.CompletionStage.class.isAssignableFrom(api.getMethod("development",UUID.class,Map.class).getReturnType()),"actual async planner API");
        Class<?> procurement=Class.forName("ru.neverland.mintcontracts.api.BuildingProcurementApi");
        for(String method:List.of("confirm","status","cancel"))check(java.util.concurrent.CompletionStage.class.isAssignableFrom(procurement.getMethod(method,org.bukkit.entity.Player.class,UUID.class).getReturnType()),"actual procurement method: "+method);
        check(java.util.concurrent.CompletionStage.class.isAssignableFrom(procurement.getMethod("offers",org.bukkit.entity.Player.class,String.class).getReturnType()),"actual offers signature");
        check(java.util.concurrent.CompletionStage.class.isAssignableFrom(procurement.getMethod("quote",org.bukkit.entity.Player.class,String.class,String.class).getReturnType()),"actual quote signature");
        check(java.util.concurrent.CompletionStage.class.isAssignableFrom(procurement.getMethod("requests",org.bukkit.entity.Player.class,int.class).getReturnType()),"actual requests signature");
        Class<?> catalog=Class.forName("ru.neverland.townybuilds.construction.BlueprintCatalog");
        Class<?> generator=Class.forName("ru.neverland.townybuilds.construction.BuildingBlueprintGenerator");
        check(java.lang.reflect.Modifier.isStatic(catalog.getMethod("sitePlan",sites,int.class,generator).getModifiers()),"actual saved blueprint entry point");
        check(sites.getMethod("blueprintSet",String.class)!=null,"actual pinned blueprint setter");
        var generate=generator.getMethod("generateForArchitecture",String.class,int.class,int.class);Object instance=generator.getConstructor().newInstance();
        for(String project:List.of("town_hall","forge")){
            Object medieval=generate.invoke(instance,project,1,current);
            Map<?,?> original=(Map<?,?>)medieval.getClass().getMethod("blocks").invoke(medieval);
            check(!original.isEmpty(),project+" actual blueprint exists");
            for(int prefix:new int[]{1000,2000}){
                int architecture=prefix+current;yaml.set("architecture-version",architecture);
                Object plan=generate.invoke(instance,project,1,CityBuildSchema.architecture(yaml,current,styles));
                Map<?,?> changed=(Map<?,?>)plan.getClass().getMethod("blocks").invoke(plan);
                check(changed.keySet().equals(original.keySet()),project+" palette keeps the actual footprint");
                boolean recolored=false;
                for(var e:original.entrySet()){
                    Object before=e.getValue(),after=changed.get(e.getKey());
                    Object from=before.getClass().getMethod("material").invoke(before),to=after.getClass().getMethod("material").invoke(after);
                    if(!from.equals(to))recolored=true;
                }
                check(recolored,project+" schema reaches the real styled materials, not the medieval plan");
            }
        }
        System.out.println("PASS "+checks+" real Builds API checks; production CityBuildSchema and unchanged Builds 0.11.2, no server/world simulation");
    }
}
