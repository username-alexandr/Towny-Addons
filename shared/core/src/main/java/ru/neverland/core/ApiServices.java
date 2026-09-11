package ru.neverland.core;
import java.lang.reflect.*;
import java.util.*;
import org.bukkit.Bukkit;
/** Optional services expose absence separately from a broken integration; calls stay on the server thread. */
public final class ApiServices {
    public enum State { READY, NOT_INSTALLED, DISABLED, SERVICE_UNAVAILABLE, INCOMPATIBLE_API, INVOCATION_ERROR }
    public record Status(String plugin,String contract,State state,String detail,long at) {}
    public record Connection(String plugin,Class<?> contract,Object service,State state,String detail) {
        public boolean ready() { return state == State.READY; }
        public Object invoke(String method,Class<?>[] signature,Object... args) throws ReflectiveOperationException {
            if (!ready()) throw new IllegalStateException(plugin + ": " + state + " — " + detail);
            try { Object result=contract.getMethod(method,signature).invoke(service,args); report(plugin,contract.getName(),State.READY,""); return result; }
            catch (NoSuchMethodException | IllegalAccessException ex) { report(plugin,contract.getName(),State.INCOMPATIBLE_API,ex.toString()); throw ex; }
            catch (InvocationTargetException ex) { report(plugin,contract.getName(),State.INVOCATION_ERROR,String.valueOf(ex.getCause())); throw ex; }
            catch (RuntimeException | LinkageError ex) { report(plugin,contract.getName(),State.INVOCATION_ERROR,ex.toString()); throw ex; }
        }
    }
    private static final Map<String,Status> statuses=new HashMap<>();
    private static final Map<String,Long> warnings=new HashMap<>();
    private ApiServices() {}
    public static synchronized List<Status> statuses() { return List.copyOf(statuses.values()); }
    private static synchronized void report(String plugin,String contract,State state,String detail) {
        String key=plugin+"/"+contract; long now=System.currentTimeMillis(); Status old=statuses.put(key,new Status(plugin,contract,state,detail,now));
        if(state!=State.READY && state!=State.NOT_INSTALLED && (old==null || old.state()!=state || now-warnings.getOrDefault(key,0L)>=60000)) {
            warnings.put(key,now); Bukkit.getLogger().warning("[NeverLand API] "+plugin+" "+state+": "+detail);
        } else if(state==State.READY && old!=null && old.state()!=State.READY && old.state()!=State.NOT_INSTALLED) Bukkit.getLogger().info("[NeverLand API] "+plugin+": связь восстановлена");
    }
    private static synchronized void reportConnection(String plugin,String contract,State state,String detail) {
        Status old=statuses.get(plugin+"/"+contract);
        // A successful lookup cannot erase a failed call; only a successful invocation can.
        if(state==State.READY&&old!=null&&old.state()==State.INVOCATION_ERROR)return;
        report(plugin,contract,state,detail);
    }
    public static Connection connect(String pluginName,String contractName,int major,String... required) {
        State state;String detail="";Class<?> type=null;Object service=null;
        try {
            if(!Bukkit.isPrimaryThread())throw new IllegalStateException("API требует основного потока сервера");
            var plugin=Bukkit.getPluginManager().getPlugin(pluginName);
            if(plugin==null)state=State.NOT_INSTALLED;
            else if(!plugin.isEnabled())state=State.DISABLED;
            else {
                type=Class.forName(contractName,true,plugin.getClass().getClassLoader()); service=Bukkit.getServicesManager().load(type);
                if(service==null)state=State.SERVICE_UNAVAILABLE;
                else {
                    Object version=type.getMethod("apiVersion").invoke(service);Object caps=type.getMethod("capabilities").invoke(service);
                    if(!(version instanceof Integer n)||n!=major||!(caps instanceof Set<?> set)||!set.containsAll(Arrays.asList(required))) {state=State.INCOMPATIBLE_API;detail="Нужен API "+major+" с возможностями "+Arrays.toString(required)+"; получен "+version+" "+caps;}
                    else state=State.READY;
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | LinkageError ex) {state=State.INCOMPATIBLE_API;detail=ex.toString();}
        catch (InvocationTargetException | RuntimeException ex) {state=State.INVOCATION_ERROR;detail=ex.toString();}
        reportConnection(pluginName,contractName,state,detail);return new Connection(pluginName,type,service,state,detail);
    }
}
