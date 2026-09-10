package ru.neverland.townypower;
import org.bukkit.*;
import org.bukkit.plugin.*;
import ru.neverland.integration.BuildingOperations;
import ru.neverland.townypower.api.*;
import ru.neverland.townyupkeep.api.TownyUpkeepApi;
import java.util.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
/** Exercises the real optional-plugin lookup and service boundary without a Minecraft server. */
public final class OperationGateSmoke {
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,java.lang.reflect.InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);}
    private static Object fallback(Object proxy,java.lang.reflect.Method method,Object[] args){return switch(method.getName()){case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];case "toString"->"test fixture";default->method.getReturnType()==boolean.class?false:method.getReturnType()==int.class?0:null;};}
    public static void run()throws Exception{
        Map<String,Plugin> plugins=new HashMap<>();var services=new SimpleServicesManager();
        var manager=proxy(PluginManager.class,(p,m,a)->m.getName().equals("getPlugin")?plugins.get(a[0]):fallback(p,m,a));
        var server=proxy(Server.class,(p,m,a)->switch(m.getName()){case "getPluginManager"->manager;case "getServicesManager"->services;case "getLogger"->Logger.getLogger("power-test");case "getName","getVersion","getBukkitVersion"->"power-test";default->fallback(p,m,a);});
        // Paper setServer() prints build metadata supplied only by a real server distribution.
        var field=Bukkit.class.getDeclaredField("server");field.setAccessible(true);field.set(null,server);UUID town=UUID.randomUUID();check(BuildingOperations.active(town,"foundry"),"optional plugins absent preserve original behavior");
        var enabled=new AtomicBoolean(true);var upkeep=proxy(Plugin.class,(p,m,a)->m.getName().equals("isEnabled")?true:fallback(p,m,a));var power=proxy(Plugin.class,(p,m,a)->m.getName().equals("isEnabled")?enabled.get():fallback(p,m,a));
        plugins.put("NeverLandTownyUpkeep",upkeep);plugins.put("NeverLandTownyPower",power);var paid=new AtomicBoolean(true);var supplied=new AtomicBoolean(false);
        services.register(TownyUpkeepApi.class,(id,project)->paid.get(),upkeep,ServicePriority.Normal);
        TownyPowerApi api=new TownyPowerApi(){public boolean powered(UUID id,String project){return supplied.get();}public String status(UUID id,String project){return "Не хватает энергии";}public Optional<PowerSnapshot> power(UUID id){return Optional.empty();}public Optional<PowerSnapshot> residentPower(UUID id){return Optional.empty();}public Collection<PowerSnapshot> towns(){return List.of();}};
        services.register(TownyPowerApi.class,api,power,ServicePriority.Normal);
        check(!BuildingOperations.active(town,"foundry")&&BuildingOperations.maintained(town,"foundry"),"power outage must not feed back into maintenance or generator startup");check(BuildingOperations.inactiveReason(town,"foundry").contains("энергии"),"actual power reason shown");check(BuildingOperations.level(town,"foundry",5)==0,"operational level gated");
        supplied.set(true);check(BuildingOperations.active(town,"foundry")&&BuildingOperations.level(town,"foundry",5)==5,"power restoration resumes benefits");paid.set(false);check(!BuildingOperations.active(town,"foundry")&&BuildingOperations.inactiveReason(town,"foundry").contains("Содержание"),"upkeep still required");paid.set(true);
        enabled.set(false);check(!BuildingOperations.active(town,"foundry"),"installed failed power plugin closes gate");enabled.set(true);services.unregisterAll(power);check(!BuildingOperations.active(town,"foundry"),"missing service closes gate");plugins.remove("NeverLandTownyPower");check(BuildingOperations.active(town,"foundry"),"removing optional power restores old operation contract");
        System.out.println("OperationGateSmoke OK: actual Bukkit service lookup, optional modules, maintenance/power independence, recovery and failed providers");
    }
}
