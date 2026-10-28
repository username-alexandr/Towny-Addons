package ru.neverland.townyresources;
import java.util.*;
import ru.neverland.townyresources.integration.PowerCoverage;
import ru.neverland.townyresources.model.*;
import ru.neverland.townyresources.config.ResourcesSettings;
public final class PowerProductionSmoke {
    static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static Map<String,Object> counter(String epoch,long seen,long powered){return Map.of("required",true,"epoch",epoch,"observedNanos",seen,"poweredNanos",powered);}
    static ResourceEngine.Building building(double fraction,boolean active){return new ResourceEngine.Building(1,1,true,active,"Питание отсутствует",1,1,1,fraction);}
    public static void main(String[] args){
        UUID town=UUID.randomUUID();var coverage=new PowerCoverage();check(coverage.fraction(town,"forge",counter("boot1",0,0),false)==0,"new meter cannot invent prior production");
        check(coverage.fraction(town,"forge",counter("boot1",60,3),false)==.05,"three seconds in sixty grants only five percent");
        check(coverage.fraction(town,"forge",counter("boot1",60,3),false)==.05,"preview never consumes the production interval");
        check(coverage.fraction(town,"forge",counter("boot1",60,3),true)==.05&&coverage.fraction(town,"forge",counter("boot1",60,3),true)==0,"completed production cycle cannot reuse the same powered delta");
        check(coverage.fraction(town,"forge",counter("boot1",120,3),true)==0,"later unpowered interval does not reuse earlier backup time");
        check(coverage.fraction(town,"forge",counter("boot2",10000,9999),true)==0,"new epoch cannot turn a reset into free resource production");
        check(coverage.fraction(town,"forge",counter("boot2",10060,10029),true)==.5,"later same-epoch counters resume normal proportional work");
        check(coverage.fraction(town,"old",Map.of("required",false),true)==1,"buildings without energy requirements keep full ordinary cycle");
        boolean rejected=false;try{coverage.fraction(town,"forge",counter("boot2",1,2),true);}catch(IllegalStateException expected){rejected=true;}check(rejected,"invalid external counters fail closed");
        var display=new EnumMap<Resource,ResourcesSettings.Display>(Resource.class);var cap=new EnumMap<Resource,Long>(Resource.class);for(var r:Resource.values()){display.put(r,new ResourcesSettings.Display(r.title,r.icon));cap.put(r,1000000L);}
        var profile=new BuildingProfile("forge","Мастерская","ANVIL",true,1,5,20,Map.of(Resource.METAL,3000L),Map.of(Resource.WATER,1000L),Map.of());
        var config=new ResourcesSettings(60,false,0,0,Map.of(),cap,Map.of(),display,Map.of("forge",profile));var state=TownState.initial(Map.of(Resource.WATER,100000L));
        var partial=ResourceEngine.calculate(state,Map.of("forge",building(.05,false)),0,config,100);check(partial.state().balances().get(Resource.METAL)==150&&partial.state().balances().get(Resource.WATER)==99950,"expired backup grants only earned partial output and matching input cost");
        check(partial.activity().get("forge").status().contains("5%"),"resource menu explains the actual powered fraction");
        var off=ResourceEngine.calculate(state,Map.of("forge",building(0,false)),0,config,100);check(off.state().balances().equals(state.balances()),"no paid time means no resource input or output");
        var legacyOff=ResourceEngine.calculate(state,Map.of("forge",new ResourceEngine.Building(1,1,true,false)),0,config,100);check(legacyOff.state().balances().equals(state.balances()),"old inactive constructor remains inactive");
        var paused=state.settings(state.reserves(),Set.of("forge"),Map.of());check(ResourceEngine.calculate(paused,Map.of("forge",building(.5,true)),0,config,100).state().balances().equals(paused.balances()),"saved power cannot override a city production pause");
        var random=new Random(18434);long generated=0,consumed=0;double fractionSum=0;
        for(int i=0;i<10000;i++){double fraction=random.nextInt(10001)/10000.0;var result=ResourceEngine.calculate(state,Map.of("forge",building(fraction,false)),0,config,100);generated+=result.state().balances().get(Resource.METAL);consumed+=100000-result.state().balances().get(Resource.WATER);fractionSum+=fraction;}
        check(generated<=Math.ceil(3000*fractionSum)&&consumed>=Math.floor(1000*fractionSum),"10000 partial cycles cannot increase yield or round down costs");
        System.out.println("PowerProductionSmoke PASS "+checks+" checks including 10000 partial resource cycles");
    }
}
