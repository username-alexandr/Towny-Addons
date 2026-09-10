package ru.neverland.townylogistics.model;
import java.util.*;
public record Network(UUID town,Map<String,Node> nodes,Set<Link> links,Map<String,Route> routes) {
    public Network {Objects.requireNonNull(town);nodes=Map.copyOf(nodes);links=Set.copyOf(links);routes=Map.copyOf(routes);}
    public static Network empty(UUID town){return new Network(town,Map.of(),Set.of(),Map.of());}
    public static String id(String value){if(value==null||!value.matches("[a-z0-9_-]{1,32}"))throw new IllegalArgumentException("ID: до 32 латинских букв, цифр, _ и -");return value;}
    public enum Kind {BUILDING("Здание"),ROAD("Дорожный узел"),RAIL("Железнодорожный узел"),PORT("Портовый узел");public final String title;Kind(String title){this.title=title;}}
    public record Node(String id,String project,Kind kind,Position point){public Node{Network.id(id);Objects.requireNonNull(project);Objects.requireNonNull(kind);Objects.requireNonNull(point);if((kind==Kind.BUILDING)!=!project.isBlank())throw new IllegalArgumentException("Тип узла не соответствует зданию");}}
    public record Link(String a,String b){public Link{Network.id(a);Network.id(b);if(a.equals(b))throw new IllegalArgumentException("Нужны разные узлы");if(a.compareTo(b)>0){String t=a;a=b;b=t;}}}
    public record Route(String id,String hub,String source,String target,String filter,int limit,int keep,boolean enabled){
        public Route{Network.id(id);Network.id(hub);Network.id(source);Network.id(target);Objects.requireNonNull(filter);if(source.equals(target)||limit<1||limit>1024||keep<0||keep>100000)throw new IllegalArgumentException("Некорректные параметры маршрута");}
        public Route enabled(boolean value){return new Route(id,hub,source,target,filter,limit,keep,value);}
    }
    public Node node(String id){var n=nodes.get(id);if(n==null)throw new IllegalArgumentException("Узел не найден: "+id);return n;}
    public Route route(String id){var v=routes.get(id);if(v==null)throw new IllegalArgumentException("Маршрут не найден: "+id);return v;}
    public List<Position> path(String from,String to,double maximum){
        node(from);node(to);Map<String,Double> distance=new HashMap<>();Map<String,String> previous=new HashMap<>();
        record Visit(String id,double distance){}var queue=new PriorityQueue<Visit>(Comparator.comparingDouble(Visit::distance));distance.put(from,0.0);queue.add(new Visit(from,0));
        while(!queue.isEmpty()){
            var current=queue.remove();if(current.distance()!=distance.get(current.id()))continue;if(current.id().equals(to))break;
            for(Link edge:links){String next=edge.a().equals(current.id())?edge.b():edge.b().equals(current.id())?edge.a():null;if(next==null)continue;
                double value=current.distance()+node(current.id()).point().distance(node(next).point());
                if(value<=maximum&&value<distance.getOrDefault(next,Double.POSITIVE_INFINITY)){distance.put(next,value);previous.put(next,current.id());queue.add(new Visit(next,value));}
            }
        }
        if(!distance.containsKey(to))throw new IllegalArgumentException("Нет связного пути: "+from+" → "+to);
        List<Position> result=new ArrayList<>();for(String at=to;at!=null;at=previous.get(at))result.add(node(at).point());Collections.reverse(result);return List.copyOf(result);
    }
}
