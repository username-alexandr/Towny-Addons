package ru.neverland.townybuilds.planning;
import java.util.*;
import static ru.neverland.townybuilds.planning.DevelopmentPlan.*;
public final class DevelopmentPlanSmoke {
    private static int checks;
    private static final Item OAK=new Item("OAK_LOG","Дубовое бревно","OAK_LOG","sample",false);
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    private static void bad(Runnable run){try{run.run();throw new AssertionError("Expected refusal");}catch(IllegalArgumentException expected){checks++;}}
    private static Level level(long cents,long amount){return new Level(cents,Map.of(OAK,amount),1,List.of());}
    private static Project project(String id,Map<String,Integer> dependencies){return new Project(id,id,dependencies,Map.of(1,level(100,10),2,level(200,20)),List.of());}
    private static City city(Map<String,Integer> levels,Map<String,Fund> funds,long stock){return new City(levels,funds,Map.of(OAK,stock),true,Set.of(),Set.of());}
    public static void main(String[] args){
        var catalog=Map.of("hall",project("hall",Map.of()),"mill",project("mill",Map.of("hall",1)),"farm",project("farm",Map.of("hall",2)));
        var result=estimate(catalog,city(Map.of(),Map.of(),15),Map.of("mill",1,"farm",1));
        check(result.steps().size()==4);check(result.steps().get(0).project().equals("hall"));check(result.steps().get(1).level()==2);check(result.cents()==500);check(result.supplies().get(0).required()==50);check(result.supplies().get(0).stored()==15);check(result.supplies().get(0).missing()==35);
        var fund=Map.of("hall",new Fund(2,Map.of(OAK,12L)));result=estimate(catalog,city(Map.of("hall",1),fund,20),Map.of("mill",1,"farm",1));
        check(result.cents()==400);check(result.supplies().get(0).required()==40);check(result.supplies().get(0).donated()==12);check(result.supplies().get(0).missing()==8);
        result=estimate(catalog,city(Map.of("hall",1),Map.of("hall",new Fund(1,Map.of(OAK,999L))),0),Map.of("hall",2));check(result.supplies().get(0).donated()==0);
        result=estimate(catalog,city(Map.of("hall",2),Map.of(),0),Map.of("hall",1));check(result.steps().isEmpty());check(result.cents()==0);check(result.finalLevels().get("hall")==2);
        result=estimate(catalog,new City(Map.of(),Map.of(),Map.of(OAK,999L),false,Set.of(),Set.of()),Map.of("hall",1));check(!result.known());check(result.supplies().get(0).stored()==0);check(!result.notices().isEmpty());
        result=estimate(catalog,new City(Map.of(),Map.of(),Map.of(),true,Set.of("hall"),Set.of()),Map.of("hall",1));check(!result.known());
        result=estimate(catalog,new City(Map.of(),Map.of(),Map.of(),true,Set.of(),Set.of("hall")),Map.of("hall",1));check(!result.known());
        bad(()->estimate(catalog,city(Map.of(),Map.of(),0),Map.of()));bad(()->estimate(catalog,city(Map.of(),Map.of(),0),Map.of("hall",3)));
        var cycle=Map.of("a",project("a",Map.of("b",1)),"b",project("b",Map.of("a",1)));bad(()->estimate(cycle,city(Map.of(),Map.of(),0),Map.of("a",1)));
        check(estimate(cycle,city(Map.of("b",1),Map.of(),0),Map.of("a",1)).steps().size()==1);
        var incomplete=Map.of("hall",new Project("hall","hall",Map.of(),Map.of(2,level(100,1)),List.of()));bad(()->estimate(incomplete,city(Map.of(),Map.of(),0),Map.of("hall",2)));
        var frozen=city(Map.of(),Map.of(),10);for(int i=0;i<1000;i++){var shuffled=new ArrayList<>(catalog.entrySet());Collections.shuffle(shuffled,new Random(i));var input=new LinkedHashMap<String,Project>();shuffled.forEach(e->input.put(e.getKey(),e.getValue()));check(estimate(input,frozen,Map.of("mill",1,"farm",1)).equals(estimate(catalog,frozen,Map.of("farm",1,"mill",1))));}
        check(frozen.stock().get(OAK)==10);check(frozen.levels().isEmpty());System.out.println("DevelopmentPlanSmoke PASS "+checks);
    }
}
