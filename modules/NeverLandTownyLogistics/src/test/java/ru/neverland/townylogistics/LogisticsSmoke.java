package ru.neverland.townylogistics;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townylogistics.model.*;
import ru.neverland.townylogistics.model.Network.*;
import ru.neverland.townylogistics.config.LogisticsSettings;
import ru.neverland.townylogistics.data.LogisticsRepository;
import java.util.*;
import java.nio.file.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
public final class LogisticsSmoke {
    private static final UUID WORLD=UUID.randomUUID(),TOWN=UUID.randomUUID();
    public static void main(String[] args)throws Exception{
        YamlConfiguration c;try(var in=LogisticsSmoke.class.getResourceAsStream("/config.yml")){c=YamlConfiguration.loadConfiguration(new InputStreamReader(in,StandardCharsets.UTF_8));}var settings=LogisticsSettings.load(c);
        check(settings.hubs().equals(Set.of("warehouse","cargo_terminal","caravanserai","trade_port")),"four usable dispatch buildings");
        for(int i=2;i<=5;i++){var a=settings.level(i-1);var b=settings.level(i);check(b.couriers()>a.couriers()&&b.cargo()>a.cargo()&&b.speed()>a.speed()&&b.handling()<a.handling(),"higher level has more and faster couriers");}
        var nodes=Map.of("hub",new Node("hub","warehouse",Kind.BUILDING,p(0)),"road",new Node("road","",Kind.ROAD,p(16)),"source",new Node("source","sawmill",Kind.BUILDING,p(32)),"target",new Node("target","bakery",Kind.BUILDING,p(48)));
        var links=Set.of(new Link("hub","road"),new Link("road","source"),new Link("source","target"));var route=new Route("wood","hub","source","target","",64,8,true);var network=new Network(TOWN,nodes,links,Map.of("wood",route));
        check(new Link("hub","road").equals(new Link("road","hub")),"undirected edge canonicalization");
        var out=network.path("hub","source",200);var delivery=network.path("source","target",200);var home=network.path("target","hub",200);
        check(out.equals(List.of(p(0),p(16),p(32)))&&home.size()==4,"courier follows connected waypoints both ways");
        failure(()->network.path("hub","source",20));failure(()->new Network(TOWN,nodes,Set.of(),Map.of()).path("hub","target",200));
        check(network.path("hub","hub",1).equals(List.of(p(0))),"hub may coincide with endpoint");
        check(!NavigationPolicy.allowed(List.of(p(0),p(16),p(32)),p(32),point->point.x()!=16),"foreign or hazardous intermediate path point blocks entire path");
        check(!NavigationPolicy.allowed(List.of(p(0),p(16)),p(32),point->true),"partial vanilla path never counts as destination");
        check(!NavigationPolicy.allowed(List.of(new Position(UUID.randomUUID(),32,64,0)),p(32),point->true),"cross-world arrival impossible");
        check(NavigationPolicy.allowed(delivery,p(48),point->true),"complete safe route accepted");
        var job=new CourierJob(UUID.randomUUID(),TOWN,"wood","warehouse","sawmill","bakery",3,out,delivery,home,CourierJob.Phase.LOADING,2,p(32),100);
        var back=job.returnFromSource();check(back.path().equals(List.of(p(32),p(16),p(0))),"empty or cancelled pickup retraces outbound route, never jumps directly to target");
        check(job.waitTicks(10).handlingTicks()==90&&job.waitTicks(1000).handlingTicks()==0,"handling advances only by active server ticks");
        var moved=job.phase(CourierJob.Phase.TO_TARGET,0).move(p(40),1);check(moved.position().equals(p(40))&&moved.waypoint()==1,"movement checkpoint preserves progress");
        failure(()->new Position(WORLD,Double.NaN,0,0));failure(()->new Link("same","same"));failure(()->new Route("route","hub","source","source","",1,0,true));
        c.set("levels.3.speed",Double.NaN);failure(()->LogisticsSettings.load(c));persistence(network,moved);
        System.out.println("LogisticsSmoke OK: five levels, network paths, full arrival, unsafe path rejection, return path, active-tick timing, limits and restart persistence");
    }
    private static void persistence(Network network,CourierJob job)throws Exception{Path dir=Files.createTempDirectory("logistics-test-");Path file=dir.resolve("logistics.yml");try{
        var repo=new LogisticsRepository(file);check(repo.load().networks().isEmpty(),"new database empty");repo.save(Map.of(TOWN,network),Map.of(job.id(),job));var loaded=new LogisticsRepository(file).load();
        check(loaded.networks().get(TOWN).equals(network)&&loaded.jobs().get(job.id()).equals(job),"restart retains graph, filters and exact courier phase");
        repo.save(Map.of(TOWN,network),Map.of());check(new LogisticsRepository(file).load().jobs().isEmpty(),"completed job removal persists");
        Files.writeString(file,"schema: 1\ntowns: [broken\n");String original=Files.readString(file);failure(()->new LogisticsRepository(file).load());check(Files.readString(file).equals(original),"corrupt file not overwritten");
        Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("keep"),"original");failure(()->repo.save(Map.of(),Map.of(job.id(),job)));check(Files.readString(file.resolve("keep")).equals("original"),"failed save preserves old file");
    }finally{try(var paths=Files.walk(dir)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}}
    private static Position p(double x){return new Position(WORLD,x,64,0);}
    private interface Action{void run()throws Exception;}
    private static void failure(Action a)throws Exception{boolean failed=false;try{a.run();}catch(Exception ex){failed=true;}check(failed,"expected failure");}
    private static void check(boolean condition,String text){if(!condition)throw new AssertionError(text);}
}
