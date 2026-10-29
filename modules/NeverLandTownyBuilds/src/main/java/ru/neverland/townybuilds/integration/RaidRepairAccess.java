package ru.neverland.townybuilds.integration;

import java.util.*;
import java.util.concurrent.*;
import ru.neverland.core.*;

/** Revision-bound asynchronous debt observations; no SQL or waiting on the region/Suite thread. */
public final class RaidRepairAccess {
    private static final String PLUGIN="NeverLandTownySiegesPlus",API="ru.neverland.townysieges.api.TownyRaidRepairApi";
    private record Key(UUID town,String project){}
    public record Observation(boolean blocked,long revision){}
    private record Cached(Observation value,long expires){}
    private static final Map<Key,Cached> cache=new LinkedHashMap<>();
    private static final Set<Key> pending=new HashSet<>();
    private RaidRepairAccess() { }
    public static CompletionStage<Observation> query(UUID town,String project) {
        try(var guard=SuiteState.guard()) {
            var api=ApiServices.connect(PLUGIN,API,1,"hasRepairDebt","repairDebtRevision");
            if(api.state()==ApiServices.State.NOT_INSTALLED)return CompletableFuture.completedFuture(new Observation(false,-1));
            if(!api.ready())return CompletableFuture.failedFuture(new IllegalStateException("Сведения о ремонте после рейда недоступны"));
            try{
                Object revision=api.invoke("repairDebtRevision",new Class<?>[]{});
                if(!(revision instanceof Long value))throw new IllegalStateException("Неверная версия долгов ремонта");
                Object request=api.invoke("hasRepairDebt",new Class<?>[]{UUID.class,String.class},town,project);
                if(!(request instanceof CompletionStage<?> stage))throw new IllegalStateException("Нет подтверждаемого ответа о ремонте");
                return stage.thenApply(answer->{if(!(answer instanceof Boolean debt))throw new IllegalStateException("Неизвестный результат проверки ремонта");return new Observation(debt,value);});
            }catch(ReflectiveOperationException|RuntimeException|LinkageError error){return CompletableFuture.failedFuture(error);}
        }
    }
    /** Invoke immediately before a world mutation on its owning region under Suite. */
    public static boolean permits(Observation observation) {
        if(observation==null||observation.blocked())return false;
        var api=ApiServices.connect(PLUGIN,API,1,"repairDebtRevision");
        if(observation.revision()==-1)return api.state()==ApiServices.State.NOT_INSTALLED;
        if(!api.ready())return false;
        try{return Long.valueOf(observation.revision()).equals(api.invoke("repairDebtRevision",new Class<?>[]{}));}
        catch(ReflectiveOperationException|RuntimeException|LinkageError error){return false;}
    }
    /** Interactive paths retry while a bounded asynchronous observation is being obtained. */
    public static boolean blocked(UUID town,String project) {
        try(var guard=SuiteState.guard()) {
            var key=new Key(town,project);var known=cache.get(key);
            if(known!=null&&known.expires()>System.currentTimeMillis()&&permits(known.value()))return false;
            if(pending.size()<128&&pending.add(key))query(town,project).whenComplete((value,error)->{try(var lock=SuiteState.guard()){
                pending.remove(key);if(error==null){if(cache.size()>=256)cache.remove(cache.keySet().iterator().next());cache.put(key,new Cached(value,System.currentTimeMillis()+5000));}
            }});
            known=cache.get(key);return known==null||!permits(known.value());
        }
    }
}
