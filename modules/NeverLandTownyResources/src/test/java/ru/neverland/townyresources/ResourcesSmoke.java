package ru.neverland.townyresources;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townyresources.config.ResourcesSettings;
import ru.neverland.townyresources.data.ResourcesRepository;
import ru.neverland.townyresources.model.*;
import java.util.*;
import java.nio.file.*;
import java.io.*;

public final class ResourcesSmoke {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private interface Attempt{void run()throws Exception;}
    private static void fails(Attempt attempt,String message)throws Exception{try{attempt.run();}catch(Exception expected){return;}throw new AssertionError(message);}
    private static Map<Resource,Long> stock(Object... values){Map<Resource,Long> result=new EnumMap<>(Resource.class);for(int i=0;i<values.length;i+=2)result.put((Resource)values[i],Amounts.parse(values[i+1]));return result;}
    private static YamlConfiguration read(String name)throws Exception{try(var input=ResourcesSmoke.class.getResourceAsStream("/"+name)){check(input!=null,name);var y=new YamlConfiguration();y.load(new InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8));return y;}}
    private static BuildingProfile profile(String id,Map<Resource,Long> output,Map<Resource,Long> input,int priority){return new BuildingProfile(id,id,"STONE",true,1,5,priority,output,input,Map.of());}
    private static ResourcesSettings settings(boolean population,Map<Resource,Long> cap,BuildingProfile... profiles){Map<String,BuildingProfile> buildings=new LinkedHashMap<>();for(var p:profiles)buildings.put(p.id(),p);Map<Resource,ResourcesSettings.Display> display=new EnumMap<>(Resource.class);for(var r:Resource.values())display.put(r,new ResourcesSettings.Display(r.title,r.icon));return new ResourcesSettings(60,population,1000,2000,Map.of(),cap,Map.of(),display,buildings);}
    private static Map<String,ResourceEngine.Building> built(String id,int level,double bonus){return Map.of(id,new ResourceEngine.Building(level,bonus,true));}
    public static void main(String[] args)throws Exception{
        var defaults=ResourcesSettings.load(read("config.yml"),read("buildings.yml"));check(Resource.values().length==8,"eight resources");check(defaults.buildings().size()==91,"91 profiles");
        for(var resource:Resource.values()) {
            check(defaults.buildings().values().stream().anyMatch(p->p.produces().get(resource)>0),"producer for "+resource);
            check(defaults.buildings().values().stream().anyMatch(p->p.consumes().get(resource)>0),"consumer for "+resource);
        }
        var universityStock=TownState.initial(stock(Resource.KNOWLEDGE,10,Resource.FOOD,10,Resource.WATER,10));
        var withoutInfluence=ResourceEngine.calculate(universityStock,built("university",1,1),0,defaults,100);
        check(withoutInfluence.activity().get("university").operations()==0,"research requires influence");
        var withInfluence=ResourceEngine.calculate(universityStock.balance(Resource.INFLUENCE,1000),built("university",1,1),0,defaults,100);
        check(withInfluence.activity().get("university").operations()==1&&withInfluence.state().balances().get(Resource.INFLUENCE)==0,"research spends influence");
        check(defaults.buildings().values().stream().filter(p->p.maximumLevel()==1).count()==11,"11 wonders");
        for(var p:defaults.buildings().values()){check(!p.name().equals(p.id()),"Russian building name");check(p.produces().values().stream().anyMatch(n->n>0)||p.consumes().values().stream().anyMatch(n->n>0)||p.capacity().values().stream().anyMatch(n->n>0),"real profile "+p.id());}
        check(Amounts.parse("0.001")==1&&Amounts.parse("2,125")==2125,"fixed precision");check(Amounts.bonus(1000,1.15)==1150,"district fractional output");check(Amounts.bonus(1000,Double.NaN)==1000,"invalid district neutral");
        fails(()->Amounts.parse("NaN"),"NaN rejected");fails(()->Amounts.parse("0.0001"),"extra precision rejected");fails(()->Amounts.parse("-1"),"negative rejected");fails(()->Amounts.parse("1000000001"),"overflow rejected");check(Amounts.multiply(Amounts.MAX,10_000_000)==Amounts.MAX,"saturation without overflow");
        var cap=Amounts.mutable(Map.of());for(var r:Resource.values())cap.put(r,Amounts.parse(100));
        var producer=profile("producer",stock(Resource.WOOD,3),stock(Resource.WATER,1),10);var config=settings(false,cap,producer);
        var initial=TownState.initial(stock(Resource.WATER,10));var result=ResourceEngine.calculate(initial,built("producer",5,1.15),0,config,100);
        check(result.state().balances().get(Resource.WOOD)==17250,"five levels with district output");check(result.state().balances().get(Resource.WATER)==5000,"district never multiplies costs");check(initial.balances().get(Resource.WATER)==10000,"immutable input");
        check(ResourceEngine.calculate(initial,built("producer",0,1),0,config,100).state().balances().equals(initial.balances()),"unfinished no output");
        check(ResourceEngine.calculate(initial,Map.of("producer",new ResourceEngine.Building(5,1,false)),0,config,100).state().balances().equals(initial.balances()),"foreign footprint no output");
        var paused=initial.settings(initial.reserves(),Set.of("producer"),Map.of());check(ResourceEngine.calculate(paused,built("producer",5,1),0,config,100).state().balances().equals(initial.balances()),"pause no costs");
        var full=TownState.initial(stock(Resource.WOOD,99,Resource.WATER,10));var blocked=ResourceEngine.calculate(full,built("producer",1,1),0,config,100);check(blocked.state().balances().equals(full.balances()),"full output does not consume input");
        var above=TownState.initial(stock(Resource.WOOD,150,Resource.WATER,10));check(ResourceEngine.calculate(above,built("producer",1,1),0,config,100).state().balances().get(Resource.WOOD)==150000,"capacity loss preserves stock");
        var reserve=initial.settings(stock(Resource.WATER,9),Set.of(),Map.of());var partial=ResourceEngine.calculate(reserve,built("producer",5,1),0,config,100);check(partial.activity().get("producer").operations()==1,"partial levels respect reserve");
        var converter=profile("converter",stock(Resource.MATERIALS,4),stock(Resource.WOOD,2,Resource.STONE,1),20);var materials=settings(false,cap,converter);
        var missing=TownState.initial(stock(Resource.WOOD,10));check(ResourceEngine.calculate(missing,built("converter",5,1),0,materials,100).state().balances().equals(missing.balances()),"missing one input spends nothing");
        var primary=profile("primary",stock(Resource.WOOD,2,Resource.STONE,1),Map.of(),10);var chain=settings(false,cap,primary,converter);var nodes=Map.of("primary",new ResourceEngine.Building(1,1,true),"converter",new ResourceEngine.Building(1,1,true));
        var chained=ResourceEngine.calculate(TownState.initial(Map.of()),nodes,0,chain,100);check(chained.state().balances().get(Resource.MATERIALS)==4000,"ordered same-cycle production chain");
        var reverse=TownState.initial(Map.of()).settings(Map.of(),Set.of(),Map.of("converter",0));check(ResourceEngine.calculate(reverse,nodes,0,chain,100).state().balances().get(Resource.MATERIALS)==0,"custom city priority");
        var service=profile("service",stock(Resource.INFLUENCE,1),stock(Resource.FOOD,2,Resource.WATER,2),40);var citizens=settings(true,cap,service);
        var ration=TownState.initial(stock(Resource.FOOD,10,Resource.WATER,20));var fed=ResourceEngine.calculate(ration,built("service",5,1),10,citizens,100);
        check(fed.activity().get("service").operations()==0,"citizens protected first");check(fed.state().foodCoverage()==1&&fed.state().waterCoverage()==1,"full ration");check(fed.state().balances().get(Resource.FOOD)==0&&fed.state().balances().get(Resource.WATER)==0,"rations actually consumed");
        var shortage=ResourceEngine.calculate(TownState.initial(stock(Resource.FOOD,5,Resource.WATER,5)),Map.of(),10,citizens,100);check(shortage.state().foodCoverage()==0.5&&shortage.state().waterCoverage()==0.25,"shortage fractions");
        var noPeople=ResourceEngine.calculate(TownState.initial(Map.of()),Map.of(),0,citizens,100);check(noPeople.state().foodCoverage()==1&&noPeople.state().waterCoverage()==1,"zero population");
        var state=TownState.initial(defaults.initial());var all=new HashMap<String,ResourceEngine.Building>();defaults.buildings().forEach((id,p)->all.put(id,new ResourceEngine.Building(p.maximumLevel(),1.35,true)));
        for(int cycle=1;cycle<=1000;cycle++){
            var step=ResourceEngine.calculate(state,all,250,defaults,cycle*60000L);for(var r:Resource.values()){long expected=state.balances().get(r)+step.state().income().get(r)-step.state().expense().get(r);check(expected==step.state().balances().get(r),"conservation "+r+" cycle "+cycle);check(expected>=0,"no debt");}state=step.state();
        }
        check(state.cycles()==1000,"one advance per call");
        var bigCap=stock(Resource.METAL,1000000000);var bigTurn=profile("big",bigCap,bigCap,10);var bigSettings=settings(false,bigCap,bigTurn);
        var turnover=ResourceEngine.calculate(TownState.initial(bigCap),built("big",5,1),0,bigSettings,100);
        check(turnover.state().income().get(Resource.METAL)==5*Amounts.MAX&&turnover.state().expense().get(Resource.METAL)==5*Amounts.MAX,"gross turnover may exceed stock capacity");
        check(turnover.state().balances().get(Resource.METAL)==Amounts.MAX,"large turnover conservation");
        Path folder=Files.createTempDirectory("strategic-resources-smoke");Path file=folder.resolve("resources-data.yml");UUID town=UUID.randomUUID();var repo=new ResourcesRepository(file);repo.load();repo.replace(Map.of(town,turnover.state()));var highFlow=new ResourcesRepository(file);highFlow.load();check(highFlow.states().get(town).equals(turnover.state()),"large flow persistence");repo.replace(Map.of(town,state));var restarted=new ResourcesRepository(file);restarted.load();check(restarted.states().get(town).equals(state),"exact restart incl fractions and history");
        var unchanged=restarted.states().get(town);check(unchanged.cycles()==1000,"restart no offline production");restarted.replace(Map.of());var empty=new ResourcesRepository(file);empty.load();check(empty.states().isEmpty(),"removed town stays removed");
        Files.writeString(file,"schema: 1\ntowns: [broken");var damaged=new ResourcesRepository(file);fails(damaged::load,"damaged YAML rejected");String bad=Files.readString(file);fails(()->damaged.replace(Map.of(town,initial)),"write guard after failed load");check(Files.readString(file).equals(bad),"bad file not overwritten");
        Path blockedFile=folder.resolve("blocked.yml");var blockedRepo=new ResourcesRepository(blockedFile);blockedRepo.load();blockedRepo.replace(Map.of(town,initial));Files.delete(blockedFile);Files.createDirectory(blockedFile);Files.writeString(blockedFile.resolve("keep"),"keep");fails(()->blockedRepo.replace(Map.of(town,result.state())),"failed atomic replacement");check(blockedRepo.states().get(town).equals(initial),"failed write keeps memory");check(Files.readString(blockedFile.resolve("keep")).equals("keep"),"failed write keeps target");
        UUID failedReserve=UUID.randomUUID();fails(()->blockedRepo.reserve(failedReserve,town,stock(Resource.WATER,1),Map.of()),"failed escrow atomic replacement");check(blockedRepo.reservations().isEmpty()&&blockedRepo.states().get(town).equals(initial),"failed reservation changes neither receipt nor balance");
        var invalid=read("config.yml");invalid.set("population.food-per-person",Double.NaN);fails(()->ResourcesSettings.load(invalid,read("buildings.yml")),"invalid settings rejected");
        // Upkeep escrow is saved with its balance movement; schema 1 remains readable.
        var escrowFile=folder.resolve("escrow.yml");var escrow=new ResourcesRepository(escrowFile);escrow.load();escrow.replace(Map.of(town,TownState.initial(stock(Resource.FOOD,10,Resource.STONE,10))));
        UUID invoice=UUID.randomUUID();var cost=stock(Resource.FOOD,3,Resource.STONE,4);
        check(!escrow.reserve(invoice,town,cost,stock(Resource.FOOD,8)),"city/population keep blocks escrow without spending");check(escrow.reservations().isEmpty(),"failed reserve has no receipt");
        check(escrow.reserve(invoice,town,cost,stock(Resource.FOOD,5)),"sufficient free stock reserved");check(escrow.states().get(town).balances().get(Resource.FOOD)==7000,"escrow debits once");
        var escrowRestart=new ResourcesRepository(escrowFile);escrowRestart.load();check(escrowRestart.reserve(invoice,town,cost,Map.of()),"idempotent after restart");check(escrowRestart.states().get(town).balances().get(Resource.FOOD)==7000,"no duplicate debit");
        fails(()->escrowRestart.reserve(invoice,town,stock(Resource.FOOD,4),Map.of()),"same ID different payload rejected");fails(()->escrowRestart.forget(invoice),"held receipt cannot be forgotten");
        escrowRestart.replace(Map.of());check(escrowRestart.states().containsKey(town),"deleted town escrow retains refund destination");
        escrowRestart.settle(invoice,false);escrowRestart.settle(invoice,false);check(escrowRestart.states().get(town).balances().get(Resource.FOOD)==10000,"idempotent full refund");fails(()->escrowRestart.settle(invoice,true),"opposite settlement rejected");escrowRestart.forget(invoice);check(escrowRestart.reservations().isEmpty(),"terminal receipt cleanup");
        UUID paidInvoice=UUID.randomUUID();escrowRestart.reserve(paidInvoice,town,cost,Map.of());escrowRestart.settle(paidInvoice,true);escrowRestart.settle(paidInvoice,true);check(escrowRestart.states().get(town).balances().get(Resource.FOOD)==7000,"consume never debits twice");
        check(escrowRestart.held(town).get(Resource.FOOD)==0,"committed reservation releases numeric headroom");
        var maxProducer=profile("max",stock(Resource.WOOD,10),Map.of(),10);var maxSettings=settings(false,stock(Resource.WOOD,1000000000),maxProducer);
        var nearMax=TownState.initial(Map.of(Resource.WOOD,Amounts.MAX-4000));var headroom=ResourceEngine.calculate(nearMax,built("max",1,1),0,maxSettings,1,Map.of(Resource.WOOD,3000L));
        check(headroom.state().balances().get(Resource.WOOD)==Amounts.MAX-4000,"output cannot fill reserved refund headroom");
        var legacy=new YamlConfiguration();legacy.load(escrowFile.toFile());legacy.set("schema",1);legacy.set("reservations",null);legacy.save(escrowFile.toFile());var migrated=new ResourcesRepository(escrowFile);migrated.load();check(migrated.states().get(town).balances().get(Resource.FOOD)==7000&&migrated.reservations().isEmpty(),"schema 1 migration preserves all balances");
        var inactive=ResourceEngine.calculate(initial,Map.of("producer",new ResourceEngine.Building(5,1,true,false)),0,config,100);check(inactive.state().balances().equals(initial.balances()),"inactive production has no output and no inputs");check(inactive.activity().get("producer").status().contains("НЕАКТИВНО"),"inactive status shown in resource menu");
        var inactiveWarehouse=ResourceEngine.calculate(TownState.initial(defaults.initial()),Map.of("warehouse",new ResourceEngine.Building(5,1,true,false)),0,defaults,100);check(inactiveWarehouse.capacity().equals(defaults.baseCapacity()),"inactive warehouse removes strategic capacity bonus");
        System.out.println("ResourcesSmoke OK: eight resources, 91 profiles, 1000 city cycles, conservation, reserves, priorities, shortages, capacity, district fractions durable restart and upkeep escrow");
    }
}
