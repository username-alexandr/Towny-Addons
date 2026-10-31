package cc.crafto.ui.server;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** The Contracts provider owns authorization, durable quotes, funding and publication. */
final class ProcurementAccess {
    static final String API="ru.neverland.mintcontracts.api.BuildingProcurementApi";
    private static final Set<String> METHODS=Set.of("offers","quote","confirm","status","cancel","requests");
    static CompletionStage<Object> request(Plugin contracts,String method,Player player,Object... arguments){
        try{
            if(contracts==null||!contracts.isEnabled())throw new IllegalArgumentException("Закупки недоступны: аддон контрактов выключен");
            Class<?> type=Class.forName(API,false,contracts.getClass().getClassLoader());
            var registration=contracts.getServer().getServicesManager().getRegistration(type);
            if(registration==null||registration.getPlugin()!=contracts)throw new IllegalArgumentException("Закупки временно приостановлены");
            return invoke(registration.getProvider(),type,method,player,arguments);
        }catch(Exception|LinkageError failure){return CompletableFuture.failedFuture(reason(failure));}
    }
    static CompletionStage<Object> invoke(Object provider,Class<?> type,String method,Player player,Object... arguments)throws Exception{
        if(!METHODS.contains(method)||!Integer.valueOf(1).equals(type.getMethod("apiVersion").invoke(provider))
                ||!(type.getMethod("capabilities").invoke(provider) instanceof Set<?> capabilities)||!capabilities.containsAll(METHODS))
            throw new IllegalArgumentException("Для закупок обновите Contracts и CraftoUI вместе");
        Class<?>[] signature=switch(method){case "offers"->new Class<?>[]{Player.class,String.class};case "quote"->new Class<?>[]{Player.class,String.class,String.class};case "requests"->new Class<?>[]{Player.class,int.class};default->new Class<?>[]{Player.class,UUID.class};};
        Object[] args=new Object[arguments.length+1];args[0]=player;System.arraycopy(arguments,0,args,1,arguments.length);
        Object raw=type.getMethod(method,signature).invoke(provider,args);
        if(!(raw instanceof CompletionStage<?> future))throw new IllegalArgumentException("Аддон закупок вернул неподдерживаемый ответ");
        // A UI timeout never cancels or mutates the provider's financial future.
        var result=new CompletableFuture<Object>();future.whenComplete((value,error)->{if(error==null)result.complete(value);else result.completeExceptionally(reason(error));});
        return result.orTimeout(15,TimeUnit.SECONDS);
    }
    static Throwable reason(Throwable failure){while(failure.getCause()!=null&&(failure instanceof InvocationTargetException||failure instanceof CompletionException))failure=failure.getCause();return failure;}
}
