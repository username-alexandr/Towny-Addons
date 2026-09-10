package ru.neverland.townyresources.model;
import ru.neverland.townyresources.config.ResourcesSettings;
import java.util.*;
/** Pure deterministic city cycle. Citizens' food/water reserve is protected from building inputs. */
public final class ResourceEngine {
    private ResourceEngine() {}
    public record Building(int completed,double bonus,boolean owned,boolean active,String inactiveReason,double policies) { public Building(int completed,double bonus,boolean owned,boolean active,String inactiveReason){this(completed,bonus,owned,active,inactiveReason,1);} public Building(int completed,double bonus,boolean owned,boolean active){this(completed,bonus,owned,active,"содержание не оплачено");} public Building(int completed,double bonus,boolean owned){this(completed,bonus,owned,true);} }
    public record Activity(int level,int priority,int operations,String status,Map<Resource,Long> income,Map<Resource,Long> expense) {
        public Activity { income=Amounts.flows(income);expense=Amounts.flows(expense); }
    }
    public record Result(TownState state,Map<Resource,Long> capacity,Map<Resource,Long> demand,Map<String,Activity> activity) {
        public Result { capacity=Amounts.copy(capacity);demand=Amounts.copy(demand);activity=Collections.unmodifiableMap(new LinkedHashMap<>(activity)); }
    }
    public static Result calculate(TownState state,Map<String,Building> built,int people,ResourcesSettings config,long now) {
        return calculate(state,built,people,config,now,Map.of());
    }
    public static Result calculate(TownState state,Map<String,Building> built,int people,ResourcesSettings config,long now,Map<Resource,Long> held) {
        if(people<0||people>10_000_000)throw new IllegalArgumentException("Некорректное население");
        var stock=Amounts.mutable(state.balances());var cap=Amounts.mutable(config.baseCapacity());var income=Amounts.mutable(Map.of());var expense=Amounts.mutable(Map.of());
        var demand=Amounts.mutable(Map.of());if(config.populationLinked()){demand.put(Resource.FOOD,Amounts.multiply(config.foodPerPerson(),people));demand.put(Resource.WATER,Amounts.multiply(config.waterPerPerson(),people));}
        for(var p:config.buildings().values()){var b=built.get(p.id());int levels=b!=null&&b.owned()&&b.active()?p.levels(b.completed()):0;
            for(var r:Resource.values())cap.put(r,Amounts.add(cap.get(r),Amounts.multiply(p.capacity().get(r),levels)));}
        // Escrow must always be refundable within the ledger's numeric limit.
        for(var r:Resource.values())cap.put(r,Math.min(cap.get(r),Amounts.MAX-Amounts.valid(held.getOrDefault(r,0L))));
        for(var r:Resource.values()){long add=Math.min(config.baseProduction().get(r),Math.max(0,cap.get(r)-stock.get(r)));stock.put(r,stock.get(r)+add);income.put(r,add);}
        var profiles=new ArrayList<>(config.buildings().values());profiles.sort(Comparator.comparingInt((BuildingProfile p)->state.priorities().getOrDefault(p.id(),p.priority())).thenComparing(BuildingProfile::id));
        Map<String,Activity> activities=new LinkedHashMap<>();
        for(var p:profiles){var b=built.get(p.id());int level=b==null?0:p.levels(b.completed());int priority=state.priorities().getOrDefault(p.id(),p.priority());int done=0;
            String status=!p.enabled()?"Отключено в настройках":level==0?"Не построено или этап не завершён":!b.owned()?"Площадка больше не принадлежит городу":!b.active()?"НЕАКТИВНО — "+b.inactiveReason():state.paused().contains(p.id())?"Приостановлено городом":"Работает";
            var produced=Amounts.mutable(Map.of());var consumed=Amounts.mutable(Map.of());
            if(status.equals("Работает"))for(int step=0;step<level;step++){
                var output=Amounts.mutable(p.produces());for(var r:Resource.values())output.put(r,ru.neverland.integration.PolicyEffects.output(output.get(r),b.bonus(),b.policies(),Amounts.MAX));
                String blocked=null;
                for(var r:Resource.values()){
                    long reserve=Math.max(state.reserves().get(r),demand.get(r));
                    if(p.consumes().get(r)>Math.max(0,stock.get(r)-reserve)){blocked="Не хватает: "+config.display().get(r).name()+" (с учётом резерва)";break;}
                    long next=stock.get(r)-p.consumes().get(r)+output.get(r);
                    if(output.get(r)>0&&next>cap.get(r)){blocked="Нет места: "+config.display().get(r).name();break;}
                }
                if(blocked!=null){status=blocked;break;}
                for(var r:Resource.values()){long in=output.get(r),out=p.consumes().get(r);stock.put(r,stock.get(r)-out+in);income.put(r,Math.addExact(income.get(r),in));expense.put(r,Math.addExact(expense.get(r),out));produced.put(r,Math.addExact(produced.get(r),in));consumed.put(r,Math.addExact(consumed.get(r),out));}done++;
            }
            activities.put(p.id(),new Activity(level,priority,done,status,produced,consumed));
        }
        double food=1,water=1;
        for(var r:List.of(Resource.FOOD,Resource.WATER)){long wanted=demand.get(r),paid=Math.min(wanted,stock.get(r));stock.put(r,stock.get(r)-paid);expense.put(r,Math.addExact(expense.get(r),paid));double coverage=wanted==0?1:(double)paid/wanted;if(r==Resource.FOOD)food=coverage;else water=coverage;}
        var next=new TownState(stock,state.reserves(),state.paused(),state.priorities(),Math.addExact(state.cycles(),1),now,income,expense,food,water);
        return new Result(next,cap,demand,activities);
    }
}
