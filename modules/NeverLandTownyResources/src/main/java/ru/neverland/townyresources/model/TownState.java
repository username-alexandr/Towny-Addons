package ru.neverland.townyresources.model;
import java.util.*;
public record TownState(Map<Resource,Long> balances,Map<Resource,Long> reserves,Set<String> paused,Map<String,Integer> priorities,
                        long cycles,long lastCycle,Map<Resource,Long> income,Map<Resource,Long> expense,double foodCoverage,double waterCoverage) {
    public TownState {
        balances=Amounts.copy(balances);reserves=Amounts.copy(reserves);income=Amounts.flows(income);expense=Amounts.flows(expense);
        paused=Set.copyOf(paused);priorities=Map.copyOf(priorities);
        for(String id:paused) validId(id);for(var e:priorities.entrySet()) { validId(e.getKey());if(e.getValue()<0||e.getValue()>100) throw new IllegalArgumentException("Приоритет: 0..100"); }
        if(cycles<0||lastCycle<0||!Double.isFinite(foodCoverage)||!Double.isFinite(waterCoverage)||foodCoverage<0||foodCoverage>1||waterCoverage<0||waterCoverage>1) throw new IllegalArgumentException("Некорректная история ресурсов");
    }
    private static void validId(String id) { if(id==null||!id.matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException("Некорректный ID здания"); }
    public static TownState initial(Map<Resource,Long> values) { return new TownState(values,Map.of(),Set.of(),Map.of(),0,0,Map.of(),Map.of(),1,1); }
    public TownState settings(Map<Resource,Long> keep,Set<String> stop,Map<String,Integer> order) { return new TownState(balances,keep,stop,order,cycles,lastCycle,income,expense,foodCoverage,waterCoverage); }
    public TownState balance(Resource resource,long amount) { var next=Amounts.mutable(balances);next.put(resource,Amounts.valid(amount));return new TownState(next,reserves,paused,priorities,cycles,lastCycle,income,expense,foodCoverage,waterCoverage); }
}
