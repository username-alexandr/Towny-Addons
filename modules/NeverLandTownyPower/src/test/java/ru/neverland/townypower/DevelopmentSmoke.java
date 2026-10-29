package ru.neverland.townypower;

import java.util.*;
import ru.neverland.townypower.config.PowerSettings;
import ru.neverland.townypower.model.*;
import ru.neverland.townypower.model.PowerEngine.Building;

public final class DevelopmentSmoke {
    static int checks;static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    interface Work{void run();}static void reject(Work action,String why){try{action.run();}catch(RuntimeException good){checks++;return;}throw new AssertionError(why);}
    static long value(Map<String,Object> result,String part,String key){return((Number)((Map<?,?>)result.get(part)).get(key)).longValue();}
    static Building built(int level){return new Building(level,true,true,1,true);}
    public static void main(String[] args){
        var zero=List.of(0L,0L,0L,0L,0L);var gen=new PowerProfile("generator","Генератор","REDSTONE",true,0,List.of(10L,20L,30L,40L,50L),zero);var use=new PowerProfile("factory","Завод","BRICKS",true,50,zero,List.of(5L,10L,15L,20L,25L));var settings=new PowerSettings(5,Map.of(gen.id(),gen,use.id(),use));var controls=TownPowerState.empty().charge(100000);var current=new HashMap<>(Map.of("generator",built(1),"factory",built(1)));var allowed=Map.of("generator",true,"factory",true);
        var v=DevelopmentEstimate.calculate(settings,current,Map.of("factory",3),controls,allowed);check(value(v,"before","generation")==10&&value(v,"before","demand")==5&&value(v,"before","deficit")==0,"before mirrors steady-state actual allocation");check(value(v,"after","generation")==10&&value(v,"after","demand")==15&&value(v,"after","deficit")==15,"whole-building shortage is not reduced by a fictitious partial feed");check(value(v,"delta","demand")==10&&value(v,"delta","deficit")==15,"target-level delta uses final level, not additive levels");check(controls.charge()==100000&&value(v,"after","deficit")==15,"saved battery charge is neither spent nor counted as endless production");check(v.get("periodSeconds").equals(5)&&v.get("conditionalProjects").equals(List.of("factory")),"conditional completion and current interval explicitly exposed");
        var upgraded=DevelopmentEstimate.calculate(settings,current,Map.of("generator",2,"factory",3),controls,allowed);check(value(upgraded,"after","generation")==20&&value(upgraded,"after","deficit")==0,"planned generator removes steady-state deficit");
        var stopped=DevelopmentEstimate.calculate(settings,current,Map.of("generator",2),controls.stop("generator",true),allowed);check(value(stopped,"after","generation")==0,"current manual shutdown is preserved after upgrade");
        for(var unavailable:List.of(new Building(1,false,true,1,true),new Building(1,true,false,1,true))){var blocked=Map.of("generator",unavailable,"factory",built(1));check(value(DevelopmentEstimate.calculate(settings,blocked,Map.of("generator",3),controls,allowed),"after","generation")==0,"foreign or unpaid existing site cannot become powered through a plan");}
        var future=DevelopmentEstimate.calculate(settings,Map.of("factory",built(3)),Map.of("generator",2),controls,allowed);check(value(future,"after","generation")==20&&future.get("conditionalProjects").equals(List.of("generator")),"new site output is explicitly conditional and uses no invented district bonus");
        var exclusive=DevelopmentEstimate.calculate(settings,Map.of("factory",built(3)),Map.of("generator",2),controls,Map.of("generator",false));check(value(exclusive,"after","generation")==0,"specialization lock remains effective in future scenario");
        var district=DevelopmentEstimate.calculate(settings,Map.of("generator",new Building(1,true,true,1.5,true)),Map.of("generator",2),controls,allowed);check(value(district,"after","generation")==30,"existing site's current district multiplier retained explicitly");
        var same=DevelopmentEstimate.calculate(settings,current,Map.of("generator",1,"factory",1),controls,allowed);check(value(same,"delta","generation")==0&&((List<?>)same.get("conditionalProjects")).isEmpty(),"all-final-level payload does not reclassify unchanged sites as future");
        current.clear();check(value(v,"before","generation")==10,"caller mutation cannot change saved forecast");reject(()->DevelopmentEstimate.levels(Map.of("generator",6)),"unsupported level is rejected without clamp");reject(()->DevelopmentEstimate.calculate(settings,Map.of("generator",built(5)),Map.of("generator",4),controls,allowed),"upgrade API rejects a downgrade");reject(()->v.put("known",false),"outer result immutable");reject(()->((Map<Object,Object>)v.get("after")).put("generation",9),"totals immutable");reject(()->((List<Object>)v.get("conditionalProjects")).clear(),"conditions immutable");
        check(((List<?>)v.get("buildings")).stream().noneMatch(r->((Map<?,?>)r).containsKey("powered")),"estimate does not advertise runtime power guarantees");
        System.out.println("DevelopmentSmoke PASS "+checks+" checks");
    }
}
