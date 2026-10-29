package ru.neverland.townylogistics;

import java.util.*;
import java.util.concurrent.*;
import ru.neverland.townylogistics.model.*;
import ru.neverland.townylogistics.model.Network.*;
import ru.neverland.townylogistics.service.*;

public final class RoutePlanningSmoke {
    private static final UUID WORLD=UUID.fromString("00000000-0000-0000-0000-000000000001"),TOWN=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static int checks;
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);checks++;}
    private static Node node(String id,int x,int z){return new Node(id,"",Kind.ROAD,new Position(WORLD,x,64,z));}
    public static void main(String[] args)throws Exception{
        var nodes=Map.of("a",node("a",0,0),"b",node("b",8,0),"c",node("c",8,8),"d",node("d",16,0),"e",node("e",8,-8),"x",node("x",160,0),"y",node("y",176,0));
        var links=Set.of(new Link("a","b"),new Link("b","d"),new Link("a","c"),new Link("c","d"),new Link("a","e"),new Link("e","d"),new Link("x","y"));var graph=new Network(TOWN,nodes,links,Map.of());
        var plan=RoutePlanner.plan(nodes,links,"a","d",Set.of(),100,3);check(plan.paths().size()==3,"primary plus two alternatives");check(plan.paths().get(0).nodes().equals(List.of("a","b","d")),"shortest primary path");check(plan.paths().get(1).nodes().equals(List.of("a","c","d"))&&plan.paths().get(2).nodes().equals(List.of("a","e","d")),"equal distances use stable node ordering");
        var blocked=RoutePlanner.plan(nodes,links,"a","d",Set.of(new Link("a","b")),100,3);check(blocked.paths().size()==2&&blocked.paths().stream().noneMatch(p->p.links().contains(new Link("a","b"))),"blocked link excluded from every alternative");
        check(RoutePlanner.plan(nodes,links,"a","d",Set.of(),10,3).code().equals("DISTANCE"),"explicit distance refusal");check(RoutePlanner.plan(nodes,links,"a","y",Set.of(),1000,3).code().equals("DISCONNECTED"),"explicit disconnected refusal");check(RoutePlanner.plan(nodes,links,"missing","d",Set.of(),100,3).code().equals("MISSING_NODE"),"missing endpoint refusal");
        var random=new Random(20261029);for(int trial=0;trial<150;trial++){
            var smallNodes=new LinkedHashMap<String,Node>();for(int i=0;i<7;i++)smallNodes.put("n"+i,node("n"+i,i*3,random.nextInt(13)));
            var smallLinks=new HashSet<Link>();for(int i=0;i<7;i++)for(int j=i+1;j<7;j++)if(random.nextBoolean())smallLinks.add(new Link("n"+i,"n"+j));
            var expected=new ArrayList<List<String>>();enumerate(smallLinks,"n0","n6",new ArrayList<>(),expected);
            expected.sort(Comparator.comparingDouble((List<String> p)->distance(smallNodes,p)).thenComparing(p->String.join("/",p)));
            var actual=RoutePlanner.plan(smallNodes,smallLinks,"n0","n6",Set.of(),1000,3).paths().stream().map(RoutePlanner.Path::nodes).toList();
            check(actual.equals(expected.stream().limit(3).toList()),"independent exhaustive simple paths trial "+trial+": "+actual+" vs "+expected.stream().limit(3).toList());
        }
        for(int i=0;i<1000;i++){var order=new ArrayList<>(nodes.entrySet());Collections.shuffle(order,random);var shuffled=new LinkedHashMap<String,Node>();order.forEach(e->shuffled.put(e.getKey(),e.getValue()));var edgeOrder=new ArrayList<>(links);Collections.shuffle(edgeOrder,random);check(RoutePlanner.plan(shuffled,new LinkedHashSet<>(edgeOrder),"a","d",Set.of(),100,3).paths().equals(plan.paths()),"1000 graph insertion orders produce identical paths");}
        var out=List.of(nodes.get("a").point(),nodes.get("b").point(),nodes.get("d").point());var home=new ArrayList<>(out);Collections.reverse(home);
        var job=new CourierJob(UUID.randomUUID(),TOWN,"r","warehouse","sawmill","bakery",1,out,out,home,CourierJob.Phase.TO_TARGET,1,new Position(WORLD,3,64,0),0);
        var rerouted=job.reroute(blocked.paths().get(0).points(),job.position(),true,"Проверенный обход");
        check(rerouted.id().equals(job.id())&&rerouted.source().equals(job.source())&&rerouted.target().equals(job.target())&&rerouted.position().equals(job.position()),"same shipment and physical position after reroute");check(rerouted.waypoint()==0&&rerouted.path().get(0).equals(nodes.get("a").point()),"first destination is last reached node, not next alternative point");check(rerouted.detours()==1&&rerouted.outbound().equals(job.outbound())&&rerouted.home().equals(job.home()),"only current leg is replaced");
        reject(()->job.reroute(blocked.paths().get(0).points(),job.position(),false,""),"unverified backtrack never accepted");reject(()->job.reroute(List.of(nodes.get("b").point(),nodes.get("d").point()),job.position(),true,""),"cannot skip last reached anchor");reject(()->job.reroute(List.of(nodes.get("a").point(),nodes.get("e").point()),job.position(),true,""),"delivery destination cannot change");
        var cache=new RouteCache();try{
            await(cache,graph,"a","d");await(cache,graph,"x","y");long before=cache.calculations();
            cache.invalidateLinks(TOWN,Set.of(new Link("a","b")));await(cache,graph,"x","y");check(cache.calculations()==before,"unrelated graph component retains cache");await(cache,graph,"a","d");check(cache.calculations()==before+1,"intersecting path recalculated once");
            var added=new HashSet<>(links);added.add(new Link("x","d"));var joined=new Network(TOWN,nodes,added,Map.of());cache.changed(graph,joined);await(cache,joined,"x","y");check(cache.calculations()==before+2,"new connecting edge invalidates affected component");
            var index=new RouteIndex();index.update(graph);check(index.affected(WORLD,0,0).get(TOWN).contains(new Link("a","b")),"spatial events target affected links");check(!index.affected(WORLD,0,0).get(TOWN).contains(new Link("x","y")),"distant links unaffected");index.remove(TOWN);check(index.affected(WORLD,0,0).isEmpty(),"removed network releases geometry index");
        }finally{cache.prepareClose().toCompletableFuture().get(5,TimeUnit.SECONDS);}
        check(cache.read(graph,"a","d",Set.of(),100).status().equals("CLOSED"),"no new path work after stop");
        System.out.println("RoutePlanningSmoke PASS "+checks+" checks (150 exhaustive graphs, 1000 shuffled graphs)");
    }
    private static void enumerate(Set<Link> links,String at,String target,List<String> path,List<List<String>> result){if(path.contains(at))return;path.add(at);if(at.equals(target))result.add(List.copyOf(path));else{var next=new TreeSet<String>();for(var edge:links)if(edge.a().equals(at))next.add(edge.b());else if(edge.b().equals(at))next.add(edge.a());for(String n:next)enumerate(links,n,target,path,result);}path.remove(path.size()-1);}
    private static double distance(Map<String,Node> nodes,List<String> path){double total=0;for(int i=1;i<path.size();i++)total+=nodes.get(path.get(i-1)).point().distance(nodes.get(path.get(i)).point());return total;}
    private static void await(RouteCache cache,Network graph,String from,String to)throws Exception{long deadline=System.nanoTime()+5_000_000_000L;while(System.nanoTime()<deadline){var read=cache.read(graph,from,to,Set.of(),1000);if(read.status().equals("READY")){check(read.result().ready(),"cache found path");return;}if(read.status().equals("FAILED"))throw new AssertionError(read.explanation());Thread.sleep(2);}throw new AssertionError("path cache timeout");}
    private static void reject(Runnable run,String reason){boolean failed=false;try{run.run();}catch(IllegalArgumentException expected){failed=true;}check(failed,reason);}
}
