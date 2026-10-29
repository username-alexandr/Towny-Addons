package ru.neverland.townyupkeep;

import java.math.BigDecimal;
import java.util.*;
import ru.neverland.townyupkeep.config.UpkeepSettings;
import ru.neverland.townyupkeep.model.*;

public final class DevelopmentSmoke {
    static int checks;static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    interface Work{void run();}static void reject(Work action,String why){try{action.run();}catch(RuntimeException good){checks++;return;}throw new AssertionError(why);}
    static Map<?,?> part(Map<String,Object> result,String part){return(Map<?,?>)result.get(part);}
    static long money(Map<String,Object> result,String key){return((Number)part(result,key).get("cents")).longValue();}
    static UpkeepSettings settings(boolean enabled){var profile=new Profile("old","Старое здание","BRICKS",true,1,new Cost(1000,Map.of("wood",1000L)));var newer=new Profile("new","Новое здание","BRICKS",enabled,2,new Cost(1000,Map.of("wood",1000L)));return new UpkeepSettings(3600,3600,60,1,BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("0.1"),new BigDecimal("5"),Map.of("old",profile,"new",newer));}
    public static void main(String[] args){
        var s=settings(true);var current=new HashMap<>(Map.of("old",1));var plan=new HashMap<>(Map.of("old",1,"new",1));var allowed=Map.of("old",true,"new",true);var multipliers=Map.of("old",1.0,"new",1.0);
        var v=DevelopmentEstimate.calculate(s,0,0,current,plan,Set.of(),allowed,multipliers);check(money(v,"before")==1100&&money(v,"after")==2400&&money(v,"delta")==1300,"new building increases rate of existing buildings through whole-town size factor");check(((Map<?,?>)part(v,"delta").get("resources")).get("wood").equals(1300L),"resources use exact thousandths and whole-city difference");
        check(v.get("periodSeconds").equals(3600)&&part(v,"before").get("sizeFactor").equals("1.1")&&part(v,"after").get("sizeFactor").equals("1.2"),"period and both size multipliers are explicit");
        var policy=DevelopmentEstimate.calculate(s,0,0,current,plan,Set.of(),allowed,Map.of("old",1.5,"new",1.0));check(money(policy,"before")==1650&&money(policy,"after")==3000,"current Policies multiplier applies to existing and planned levels");
        var stopped=DevelopmentEstimate.calculate(s,0,0,current,plan,Set.of("old"),allowed,multipliers);check(money(stopped,"before")==0&&money(stopped,"after")==1200,"paused buildings stay paused but retain city-size contribution");
        var exclusive=DevelopmentEstimate.calculate(s,0,0,current,plan,Set.of(),Map.of("old",true,"new",false),multipliers);check(money(exclusive,"before")==money(exclusive,"after"),"unavailable specialization cannot create hypothetical active upkeep");
        var disabled=DevelopmentEstimate.calculate(settings(false),0,0,current,plan,Set.of(),allowed,multipliers);check(money(disabled,"after")==1200,"disabled profile has no new bill but still changes actual size factor");
        var same=DevelopmentEstimate.calculate(s,0,0,current,Map.of("old",1),Set.of(),allowed,multipliers);check(money(same,"delta")==0,"unchanged final levels have no artificial extra cost");
        var upgrade=DevelopmentEstimate.calculate(s,0,0,current,Map.of("old",3),Set.of(),allowed,multipliers);check(money(upgrade,"after")==3900,"upgrade quotes target level instead of adding it to existing level");
        var missingProfile=DevelopmentEstimate.calculate(s,0,0,current,Map.of("unpriced",1),Set.of(),Map.of("old",true,"unpriced",true),multipliers);check(money(missingProfile,"after")==1200,"unconfigured building has no invented charge, but increases size like production scan");
        current.put("old",5);plan.clear();check(money(v,"before")==1100&&money(v,"after")==2400,"returned forecast does not change with caller input mutation");
        reject(()->DevelopmentEstimate.levels(Map.of("old",6)),"unsupported levels cannot silently clamp to five");reject(()->DevelopmentEstimate.levels(Map.of("old",0)),"zero levels are not target constructions");reject(()->DevelopmentEstimate.levels(Map.of("bad.path",2)),"invalid building keys rejected");reject(()->DevelopmentEstimate.calculate(s,0,0,current,Map.of("old",4),Set.of(),allowed,multipliers),"demolition cannot masquerade as construction estimate");reject(()->DevelopmentEstimate.calculate(s,0,0,Map.of("old",1),Map.of(),Set.of(),allowed,Map.of("old",Double.NaN)),"unavailable policy value cannot imply a neutral quote");
        reject(()->v.put("known",false),"outer result immutable");reject(()->((Map<Object,Object>)v.get("after")).put("cents",0),"inner totals immutable");reject(()->((Map<Object,Object>)part(v,"after").get("resources")).put("wood",0),"resource maps immutable");reject(()->((List<Object>)v.get("buildings")).clear(),"building rows immutable");
        System.out.println("DevelopmentSmoke PASS "+checks+" checks");
    }
}
