package ru.neverland.townyresearch;
import org.bukkit.*;
import org.bukkit.plugin.*;
import ru.neverland.integration.ResearchBonuses;
import ru.neverland.townyresearch.api.*;
import java.util.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.*;
public final class BonusBridgeSmoke {
    private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> c,java.lang.reflect.InvocationHandler h){return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class<?>[]{c},h);}
    private static Object fallback(Object p,java.lang.reflect.Method m,Object[] a){return switch(m.getName()){case "hashCode"->System.identityHashCode(p);case "equals"->p==a[0];case "toString"->"fixture";default->m.getReturnType()==boolean.class?false:m.getReturnType()==int.class?0:null;};}
    public static void run()throws Exception{
        Map<String,Plugin> plugins=new HashMap<>();var services=new SimpleServicesManager();var manager=proxy(PluginManager.class,(p,m,a)->m.getName().equals("getPlugin")?plugins.get(a[0]):fallback(p,m,a));
        var server=proxy(Server.class,(p,m,a)->switch(m.getName()){case "getPluginManager"->manager;case "getServicesManager"->services;case "isPrimaryThread"->true;default->fallback(p,m,a);});var field=Bukkit.class.getDeclaredField("server");field.setAccessible(true);Object previous=field.get(null);field.set(null,server);
        try{UUID town=UUID.randomUUID();check(ResearchBonuses.production(town,"foundry")==1,"optional research absent is neutral");var enabled=new AtomicBoolean(true);var broken=new AtomicBoolean(false);var value=new AtomicReference<Double>(.2);var plugin=proxy(Plugin.class,(p,m,a)->m.getName().equals("isEnabled")?enabled.get():fallback(p,m,a));plugins.put("NeverLandTownyResearch",plugin);
            var api=proxy(TownyResearchApi.class,(p,m,a)->{if(m.getName().equals("apiVersion"))return 1;if(m.getName().equals("capabilities"))return Set.of("bonus","technology","research");if(m.getName().equals("bonus")){if(broken.get())throw new IllegalStateException("provider failed");check(a[0].equals(town)&&a[1].equals("metallurgy"),"correct city and technology");return value.get();}return fallback(p,m,a);});services.register(TownyResearchApi.class,api,plugin,ServicePriority.Normal);
            check(ResearchBonuses.production(town,"foundry")==1.2,"real reflective service resolves bonus");check(ResearchBonuses.production(town,"warehouse")==1,"unrelated project has no effect");value.set(Double.NaN);check(ResearchBonuses.production(town,"foundry")==1,"invalid service value neutral");value.set(10d);check(ResearchBonuses.bonus(town,"metallurgy")==.5,"upper cap");broken.set(true);check(ResearchBonuses.production(town,"foundry")==1,"provider error neutral");broken.set(false);enabled.set(false);check(ResearchBonuses.production(town,"foundry")==1,"disabled module neutral");enabled.set(true);services.unregisterAll(plugin);check(ResearchBonuses.production(town,"foundry")==1,"missing service neutral");
        }finally{field.set(null,previous);}System.out.println("BonusBridgeSmoke OK: actual Bukkit lookup, technology mapping, absent/disabled/failed provider and bounds");
    }
}
