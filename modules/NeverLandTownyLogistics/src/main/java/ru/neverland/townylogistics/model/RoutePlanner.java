package ru.neverland.townylogistics.model;

import java.util.*;
import ru.neverland.townylogistics.model.Network.*;

/** Bounded deterministic simple paths over immutable graph data; never reads Bukkit or acquires SuiteState. */
public final class RoutePlanner {
    public static final int MAX_NODES=512,MAX_EDGES=16384,MAX_VISITS=250_000;
    public record Path(List<String> nodes,List<Position> points,Set<Link> links,double length){public Path{nodes=List.copyOf(nodes);points=List.copyOf(points);links=Set.copyOf(links);}}
    public record Result(List<Path> paths,String code,String explanation,int visited){public Result{paths=List.copyOf(paths);}public boolean ready(){return !paths.isEmpty();}}
    private static final class Budget{int remaining=MAX_VISITS,used;boolean take(){if(Thread.currentThread().isInterrupted()||remaining--<=0)return false;used++;return true;}}
    private record Visit(String node,double distance,String key){}
    private RoutePlanner(){}
    public static double distance(Position a,Position b){return a.world().equals(b.world())?Math.hypot(Math.hypot(a.x()-b.x(),a.z()-b.z()),a.y()-b.y()):Double.POSITIVE_INFINITY;}
    public static Result plan(Map<String,Node> nodes,Set<Link> links,String from,String to,Set<Link> excluded,double maximum,int alternatives){
        if(nodes.size()>MAX_NODES||links.size()>MAX_EDGES||alternatives<1||alternatives>3||!Double.isFinite(maximum)||maximum<=0)throw new IllegalArgumentException("Сеть или предел поиска слишком велики");
        if(!nodes.containsKey(from)||!nodes.containsKey(to))return new Result(List.of(),"MISSING_NODE","Точка маршрута удалена: проверьте отправителя и получателя",0);
        if(!nodes.get(from).point().world().equals(nodes.get(to).point().world()))return new Result(List.of(),"WORLD","Курьер не может перейти в другой мир",0);
        var adjacency=new TreeMap<String,List<String>>();for(String id:nodes.keySet())adjacency.put(id,new ArrayList<>());for(var link:links){if(!nodes.containsKey(link.a())||!nodes.containsKey(link.b()))throw new IllegalArgumentException("Связь ссылается на удалённый узел");if(nodes.get(link.a()).point().world().equals(nodes.get(link.b()).point().world())){adjacency.get(link.a()).add(link.b());adjacency.get(link.b()).add(link.a());}}adjacency.values().forEach(Collections::sort);
        var budget=new Budget();var first=shortest(nodes,adjacency,from,to,excluded,Set.of(),maximum,budget);if(first==null){if(budget.remaining<0)return new Result(List.of(),"SEARCH_LIMIT","Пересчёт достиг предела работы; упростите сеть",budget.used);var unbounded=shortest(nodes,adjacency,from,to,excluded,Set.of(),Double.POSITIVE_INFINITY,budget);return new Result(List.of(),unbounded!=null?"DISTANCE":"DISCONNECTED",unbounded!=null?"Обход превышает допустимую длину рейса":"Нет связного обхода: добавьте другую дорогу между точками",budget.used);}
        var accepted=new ArrayList<Path>();accepted.add(first);var candidates=new PriorityQueue<Path>(Comparator.comparingDouble(Path::length).thenComparing(p->String.join("/",p.nodes())));var known=new HashSet<String>();known.add(String.join("/",first.nodes()));
        for(int count=1;count<alternatives&&budget.remaining>=0;count++){
            var previous=accepted.get(count-1);double rootLength=0;
            for(int spur=0;spur<previous.nodes().size()-1&&budget.remaining>=0;spur++){
                var root=previous.nodes().subList(0,spur+1);var blocked=new HashSet<>(excluded);for(var path:accepted)if(path.nodes().size()>spur+1&&path.nodes().subList(0,spur+1).equals(root))blocked.add(new Link(path.nodes().get(spur),path.nodes().get(spur+1)));
                var forbidden=new HashSet<>(root);forbidden.remove(previous.nodes().get(spur));var tail=shortest(nodes,adjacency,previous.nodes().get(spur),to,blocked,forbidden,maximum-rootLength,budget);
                if(tail!=null){var sequence=new ArrayList<>(root.subList(0,root.size()-1));sequence.addAll(tail.nodes());String key=String.join("/",sequence);if(known.add(key))candidates.add(path(nodes,sequence));}
                rootLength+=distance(nodes.get(previous.nodes().get(spur)).point(),nodes.get(previous.nodes().get(spur+1)).point());
            }
            if(candidates.isEmpty())break;accepted.add(candidates.remove());
        }
        return new Result(accepted,budget.remaining<0?"LIMITED_ALTERNATIVES":"READY",budget.remaining<0?"Основной путь найден; поиск дополнительных вариантов ограничен":"",budget.used);
    }
    private static Path shortest(Map<String,Node> nodes,Map<String,List<String>> adjacency,String from,String to,Set<Link> excluded,Set<String> forbidden,double maximum,Budget budget){
        if(maximum<0||forbidden.contains(from)||forbidden.contains(to))return null;var distances=new HashMap<String,Double>();var keys=new HashMap<String,String>();var previous=new HashMap<String,String>();var queue=new PriorityQueue<Visit>(Comparator.comparingDouble(Visit::distance).thenComparing(Visit::key));distances.put(from,0.0);keys.put(from,from);queue.add(new Visit(from,0,from));var settled=new HashSet<String>();
        while(!queue.isEmpty()){
            if(!budget.take())return null;var current=queue.remove();if(!settled.add(current.node()))continue;if(current.node().equals(to)){var result=new ArrayList<String>();for(String at=to;at!=null;at=previous.get(at))result.add(at);Collections.reverse(result);return path(nodes,result);}
            for(String next:adjacency.get(current.node())){if(!budget.take())return null;if(forbidden.contains(next)||settled.contains(next)||excluded.contains(new Link(current.node(),next)))continue;double proposed=current.distance()+distance(nodes.get(current.node()).point(),nodes.get(next).point());String key=current.key()+"/"+next;double old=distances.getOrDefault(next,Double.POSITIVE_INFINITY);if(proposed<=maximum&&(proposed<old||proposed==old&&key.compareTo(keys.getOrDefault(next,"~"))<0)){distances.put(next,proposed);keys.put(next,key);previous.put(next,current.node());queue.add(new Visit(next,proposed,key));}}
        }return null;
    }
    private static Path path(Map<String,Node> nodes,List<String> sequence){var points=sequence.stream().map(id->nodes.get(id).point()).toList();var links=new LinkedHashSet<Link>();double length=0;for(int i=1;i<sequence.size();i++){links.add(new Link(sequence.get(i-1),sequence.get(i)));length+=distance(points.get(i-1),points.get(i));}return new Path(sequence,points,links,length);}
    public static Set<String> component(Network network,Collection<String> starts){var adjacency=new HashMap<String,Set<String>>();for(var link:network.links()){adjacency.computeIfAbsent(link.a(),ignored->new HashSet<>()).add(link.b());adjacency.computeIfAbsent(link.b(),ignored->new HashSet<>()).add(link.a());}var seen=new HashSet<String>();var queue=new ArrayDeque<String>();for(String id:starts)if(network.nodes().containsKey(id)&&seen.add(id))queue.add(id);while(!queue.isEmpty()){String at=queue.remove();for(String next:adjacency.getOrDefault(at,Set.of()))if(seen.add(next))queue.add(next);}return Set.copyOf(seen);}
}
