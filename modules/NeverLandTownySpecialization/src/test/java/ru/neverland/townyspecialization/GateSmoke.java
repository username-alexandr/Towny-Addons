package ru.neverland.townyspecialization;
import org.bukkit.*;
import org.bukkit.plugin.*;
import ru.neverland.integration.*;
import ru.neverland.townyspecialization.api.*;
import java.util.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.*;
public final class GateSmoke {
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    @SuppressWarnings("unchecked")private static <T>T proxy(Class<T> type,java.lang.reflect.InvocationHandler h){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},h);}
    private static Object fallback(Object p,java.lang.reflect.Method m,Object[] a){return switch(m.getName()){case "hashCode"->System.identityHashCode(p);case "equals"->p==a[0];case "toString"->"fixture";default->m.getReturnType()==boolean.class?false:m.getReturnType()==int.class?0:null;};}
    public static void run()throws Exception{
        Map<String,Plugin> plugins=new HashMap<>();var services=new SimpleServicesManager();var manager=proxy(PluginManager.class,(p,m,a)->m.getName().equals("getPlugin")?plugins.get(a[0]):fallback(p,m,a));var server=proxy(Server.class,(p,m,a)->switch(m.getName()){case "getPluginManager"->manager;case "getServicesManager"->services;case "isPrimaryThread"->true;default->fallback(p,m,a);});var field=Bukkit.class.getDeclaredField("server");field.setAccessible(true);Object previous=field.get(null);field.set(null,server);
        try{UUID town=UUID.randomUUID(),other=UUID.randomUUID();check(BuildingOperations.active(town,"foundry"),"old ordinary buildings remain optional");for(String id:SpecializationRules.PROJECTS.keySet())check(!BuildingOperations.active(town,id),"unique buildings require specialization plugin");
            var enabled=new AtomicBoolean(true);var broken=new AtomicBoolean(false);var chosen=new AtomicReference<String>("trade");var value=new AtomicReference<Double>(.2);var plugin=proxy(Plugin.class,(p,m,a)->m.getName().equals("isEnabled")?enabled.get():fallback(p,m,a));plugins.put("NeverLandTownySpecialization",plugin);
            var api=proxy(TownySpecializationApi.class,(p,m,a)->{if(broken.get())throw new IllegalStateException("provider failed");return switch(m.getName()){case "apiVersion"->1;case "capabilities"->Set.of("bonus","canUseBuilding","productionBonus");case "canUseBuilding"->town.equals(a[0])&&chosen.get().equals(SpecializationRules.required((String)a[1]));case "bonus","productionBonus"->town.equals(a[0])?value.get():0d;default->fallback(p,m,a);};});services.register(TownySpecializationApi.class,api,plugin,ServicePriority.Normal);
            check(BuildingOperations.active(town,"trade_exchange")&&!BuildingOperations.active(town,"citadel"),"only chosen unique building active through real common gate");check(!SpecializationAccess.allowed(other,"trade_exchange"),"city isolation");check(SpecializationAccess.production(town,"trade_exchange",1.15)==1.38,"actual reflective production bonus");
            chosen.set("port");check(!BuildingOperations.active(town,"trade_exchange")&&BuildingOperations.active(town,"admiralty"),"switch deactivates old building without mutating its stored level");check(BuildingOperations.inactiveReason(town,"trade_exchange").contains("Торговый центр"),"localized lock reason");check(BuildingOperations.maintained(town,"trade_exchange")&&BuildingOperations.powered(town,"trade_exchange"),"specialization gate remains independent of maintenance and power");
            value.set(Double.NaN);check(SpecializationAccess.bonus(town,"production")==0,"invalid bonus neutral");broken.set(true);check(!SpecializationAccess.allowed(town,"admiralty")&&SpecializationAccess.bonus(town,"production")==0,"failed API locks unique buildings and bonuses");broken.set(false);enabled.set(false);check(!BuildingOperations.active(town,"admiralty"),"disabled module fails closed");enabled.set(true);services.unregisterAll(plugin);check(!BuildingOperations.active(town,"admiralty"),"missing service fails closed");plugins.clear();check(BuildingOperations.active(town,"foundry")&&!BuildingOperations.active(town,"admiralty"),"plugin removal preserves ordinary buildings and keeps unique projects locked");
        }finally{field.set(null,previous);}System.out.println("GateSmoke OK: actual Bukkit service lookup, exclusive project operations, live switching, missing/disabled providers and independent upkeep/power gates");
    }
}
