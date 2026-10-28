package ru.neverland.townyenvironment.model;
import java.util.*;
import ru.neverland.townyenvironment.config.EnvironmentSettings;
/** Pure what-if calculation; never advances the saved city simulation. */
public final class EnvironmentForecast {
    private EnvironmentForecast(){}
    public static Map<String,Object> calculate(EnvironmentState state,Map<String,Integer> active,EnvironmentSettings settings,String building,int target,int seconds,int elapsed){
        if(target<0||target>5||seconds<1||seconds>604800||elapsed<0||elapsed>=settings.interval())throw new IllegalArgumentException("Неверный горизонт экологического прогноза");
        if(!settings.buildings().containsKey(building))return Map.of("ready",true,"relevant",false);
        var next=new HashMap<>(active);next.put(building,target);var before=EnvironmentEngine.pressure(active,settings);var after=EnvironmentEngine.pressure(next,settings);
        int cycles=(seconds+elapsed)/settings.interval();double baseline=clamp(state.pollution()+before.change()*cycles),projected=clamp(state.pollution()+after.change()*cycles);
        var effects=EnvironmentEngine.effects(new EnvironmentState(projected,state.cycles(),false),settings,true);
        var cleaners=settings.buildings().values().stream().filter(p->p.cleaning()>p.emission()).sorted(Comparator.comparingDouble((EnvironmentSettings.Profile p)->p.cleaning()-p.emission()).reversed()).limit(3).map(EnvironmentSettings.Profile::name).toList();
        return Map.ofEntries(Map.entry("ready",true),Map.entry("relevant",true),Map.entry("current",state.pollution()),Map.entry("baseline",baseline),Map.entry("projected",projected),Map.entry("change",projected-baseline),Map.entry("netPerCycle",after.change()),Map.entry("horizonSeconds",seconds),Map.entry("happiness",effects.happiness()),Map.entry("agriculture",effects.agriculture()),Map.entry("cleaners",cleaners));
    }
    private static double clamp(double value){return Math.max(0,Math.min(100,value));}
}
