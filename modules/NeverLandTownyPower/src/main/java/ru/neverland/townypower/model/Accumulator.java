package ru.neverland.townypower.model;
import java.util.*;

/** Charge is measured in power-seconds; unlike generation, it is a conserved saved quantity. */
public record Accumulator(Map<String,List<Long>> capacities,long chargePower,long dischargePower) {
    public Accumulator{
        if(chargePower<0||dischargePower<0||chargePower>1_000_000||dischargePower>1_000_000)throw new IllegalArgumentException("Мощность накопителя: 0..1000000");
        var copy=new LinkedHashMap<String,List<Long>>();capacities.forEach((id,levels)->{TownPowerState.valid(id);if(levels.isEmpty()||levels.size()>5)throw new IllegalArgumentException("Ёмкость по уровням: 1..5 значений");long previous=0;for(long v:levels){if(v<previous||v>TownPowerState.MAX_CHARGE)throw new IllegalArgumentException("Ёмкость должна расти по уровням");previous=v;}copy.put(id,List.copyOf(levels));});capacities=Collections.unmodifiableMap(copy);
    }
    public static Accumulator disabled(){return new Accumulator(Map.of(),0,0);}
    public long capacity(Map<String,PowerProfile> profiles,Map<String,PowerEngine.Building> buildings,TownPowerState state){
        long result=0;for(var e:capacities.entrySet()){var b=buildings.get(e.getKey());var p=profiles.get(e.getKey());if(b==null||p==null||!p.enabled()||b.level()==0||!b.owned()||!b.maintained()||!b.specialized())continue;result=Math.addExact(result,e.getValue().get(Math.min(b.level(),e.getValue().size())-1));}return Math.min(TownPowerState.MAX_CHARGE,result);
    }
    public record Step(TownPowerState state,PowerEngine.Grid grid,long capacity,long charged,long discharged,int seconds){}
    /** Credit only a previously completed live window; debit the whole upcoming window before publishing. */
    public Step plan(Map<String,PowerProfile> profiles,Map<String,PowerEngine.Building> buildings,TownPowerState state,long completedEnergy,int seconds){
        if(completedEnergy<0||seconds<1||seconds>60)throw new IllegalArgumentException("Неверное окно энергоснабжения");
        long capacity=capacity(profiles,buildings,state);
        long charged=Math.min(Math.max(0,capacity-state.charge()),completedEnergy);
        long available=Math.min(capacity,Math.addExact(state.charge(),charged));
        long backup=Math.min(dischargePower,available/seconds);
        var grid=PowerEngine.calculate(profiles,buildings,state,backup);
        long discharged=Math.multiplyExact(grid.backup(),seconds);
        return new Step(state.charge(Math.subtractExact(Math.addExact(state.charge(),charged),discharged)),grid,capacity,charged,discharged,seconds);
    }
}
