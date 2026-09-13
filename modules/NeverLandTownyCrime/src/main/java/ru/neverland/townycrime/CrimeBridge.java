package ru.neverland.townycrime;
import java.util.*;
import ru.neverland.core.ApiServices;
/** All integration crosses advertised public contracts, never another plugin's internals. */
public final class CrimeBridge implements TheftProcessor.Resources {
    static Object property(Object value,String name)throws ReflectiveOperationException{return value.getClass().getMethod(name).invoke(value);}
    static Object population(UUID town)throws Exception{return ((Optional<?>)ApiServices.call("NeverLandTownyPopulation","ru.neverland.townypopulation.api.TownyPopulationApi","population",new Class<?>[]{UUID.class},town)).orElseThrow(()->new IllegalStateException("Население ещё не рассчитано"));}
    public CrimeEngine.Inputs inputs(UUID town)throws Exception{
        Object p=population(town);if(!Boolean.FALSE.equals(property(p,"paused")))throw new IllegalStateException("Расчёт населения приостановлен");
        double happiness=((Number)property(property(p,"metrics"),"happiness")).doubleValue();
        int guard=((Number)ApiServices.call("NeverLandTownyBuilds","ru.neverland.townybuilds.api.TownyBuildsApi","operationalLevel",new Class<?>[]{UUID.class,String.class},town,"guard")).intValue();
        var jobs=ApiServices.connect("NeverLandTownyJobs","ru.neverland.townyjobs.api.TownyJobsApi",1,"workers");int workers=0;
        if(jobs.state()!=ApiServices.State.NOT_INSTALLED){if(!jobs.ready())throw new IllegalStateException("Служба стражи: "+jobs.state());workers=((Number)jobs.invoke("workers",new Class<?>[]{UUID.class,String.class},town,"guard")).intValue();}
        return new CrimeEngine.Inputs(happiness,guard,workers);
    }
    public Map<String,Long> available(UUID town,List<String> wanted)throws Exception{
        Object snapshot=((Optional<?>)ApiServices.call("NeverLandTownyResources","ru.neverland.townyresources.api.TownyResourcesApi","resources",new Class<?>[]{UUID.class},town)).orElseThrow(()->new IllegalStateException("Ресурсы ещё не рассчитаны"));
        if(!Boolean.FALSE.equals(property(snapshot,"paused")))throw new IllegalStateException("Виртуальные ресурсы приостановлены");
        Object state=property(snapshot,"state");var balances=(Map<?,?>)property(state,"balances");var reserves=(Map<?,?>)property(state,"reserves");var demand=(Map<?,?>)property(snapshot,"populationDemand");var result=new LinkedHashMap<String,Long>();
        for(var e:balances.entrySet()){String id=(String)property(e.getKey(),"id");if(wanted.contains(id)){long keep=Math.max(((Number)reserves.get(e.getKey())).longValue(),((Number)demand.get(e.getKey())).longValue());result.put(id,Math.max(0,((Number)e.getValue()).longValue()-keep));}}
        return Map.copyOf(result);
    }
    public String status(UUID id)throws Exception{return (String)ApiServices.call("NeverLandTownyResources","ru.neverland.townyresources.api.TownyResourcesApi","reservationStatus",new Class<?>[]{UUID.class},id);}
    public boolean reserve(UUID id,UUID town,Map<String,Long> amounts)throws Exception{return Boolean.TRUE.equals(ApiServices.call("NeverLandTownyResources","ru.neverland.townyresources.api.TownyResourcesApi","reserveResources",new Class<?>[]{UUID.class,UUID.class,Map.class},id,town,amounts));}
    public void consume(UUID id)throws Exception{ApiServices.call("NeverLandTownyResources","ru.neverland.townyresources.api.TownyResourcesApi","settleResources",new Class<?>[]{UUID.class,boolean.class},id,true);}
    public void forget(UUID id)throws Exception{ApiServices.call("NeverLandTownyResources","ru.neverland.townyresources.api.TownyResourcesApi","forgetReservation",new Class<?>[]{UUID.class},id);}
}
