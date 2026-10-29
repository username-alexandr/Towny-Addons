package ru.neverland.townylogistics.model;
import java.util.*;
public record Network(UUID town,Map<String,Node> nodes,Set<Link> links,Map<String,Route> routes) {
    public Network {Objects.requireNonNull(town);nodes=Map.copyOf(nodes);links=Set.copyOf(links);routes=Map.copyOf(routes);}
    public static Network empty(UUID town){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return new Network(town,Map.of(),Set.of(),Map.of());} }
    private static final java.util.regex.Pattern ID=java.util.regex.Pattern.compile("[a-z0-9_-]{1,32}");
    public static String id(String value){if(value==null||!ID.matcher(value).matches())throw new IllegalArgumentException("ID: до 32 латинских букв, цифр, _ и -");return value;}
    public enum Kind {BUILDING("Здание"),ROAD("Дорожный узел"),RAIL("Железнодорожный узел"),PORT("Портовый узел");public final String title;Kind(String title){this.title=title;}}
    public record Node(String id,String project,Kind kind,Position point){public Node{Network.id(id);Objects.requireNonNull(project);Objects.requireNonNull(kind);Objects.requireNonNull(point);if((kind==Kind.BUILDING)!=!project.isBlank())throw new IllegalArgumentException("Тип узла не соответствует зданию");}}
    public record Link(String a,String b){public Link{Network.id(a);Network.id(b);if(a.equals(b))throw new IllegalArgumentException("Нужны разные узлы");if(a.compareTo(b)>0){String t=a;a=b;b=t;}}}
    public record Route(String id,String hub,String source,String target,String filter,int limit,int keep,boolean enabled){
        public Route{Network.id(id);Network.id(hub);Network.id(source);Network.id(target);Objects.requireNonNull(filter);if(source.equals(target)||limit<1||limit>1024||keep<0||keep>100000)throw new IllegalArgumentException("Некорректные параметры маршрута");}
        public Route enabled(boolean value){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return new Route(id,hub,source,target,filter,limit,keep,value);} }
    }
    public Node node(String id){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {var n=nodes.get(id);if(n==null)throw new IllegalArgumentException("Узел не найден: "+id);return n;} }
    public Route route(String id){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {var v=routes.get(id);if(v==null)throw new IllegalArgumentException("Маршрут не найден: "+id);return v;} }
    public List<Position> path(String from,String to,double maximum){var result=RoutePlanner.plan(nodes,links,from,to,Set.of(),maximum,1);if(!result.ready())throw new IllegalArgumentException(result.explanation());return result.paths().get(0).points();}
}
