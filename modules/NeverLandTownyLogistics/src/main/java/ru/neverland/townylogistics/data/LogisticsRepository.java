package ru.neverland.townylogistics.data;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townylogistics.model.*;
import ru.neverland.townylogistics.model.Network.*;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;
public final class LogisticsRepository {
    public record Snapshot(Map<UUID,Network> networks,Map<UUID,CourierJob> jobs){public Snapshot{networks=Map.copyOf(networks);jobs=Map.copyOf(jobs);}}
    private final Path file;private Snapshot last;
    public LogisticsRepository(Path file){this.file=file;}
    public Snapshot load()throws Exception{
        if(!Files.exists(file))return new Snapshot(Map.of(),Map.of());var c=new YamlConfiguration();c.load(file.toFile());
        if(c.getInt("schema")!=1||c.getConfigurationSection("towns")==null||c.getConfigurationSection("jobs")==null)throw new IOException("Повреждена база логистики");
        Map<UUID,Network> towns=new HashMap<>();var root=c.getConfigurationSection("towns");
        for(String raw:root.getKeys(false)){
            UUID town=UUID.fromString(raw);var t=required(root,raw);Map<String,Node> nodes=new HashMap<>();Set<Link> links=new HashSet<>();Map<String,Route> routes=new HashMap<>();
            var n=required(t,"nodes");for(String id:n.getKeys(false)){var v=required(n,id);nodes.put(id,new Node(id,v.getString("building",""),Kind.valueOf(v.getString("kind","")),Position.decode(v.getString("point",""))));}
            for(String rawLink:t.getStringList("links")){var pair=rawLink.split(":");if(pair.length!=2)throw new IOException("Повреждена связь");var link=new Link(pair[0],pair[1]);if(!nodes.containsKey(link.a())||!nodes.containsKey(link.b())||!nodes.get(link.a()).point().world().equals(nodes.get(link.b()).point().world()))throw new IOException("Связь ссылается на неверный узел");links.add(link);}
            var rr=required(t,"routes");for(String id:rr.getKeys(false)){var v=required(rr,id);var route=new Route(id,v.getString("hub"),v.getString("source"),v.getString("target"),v.getString("filter",""),v.getInt("limit",64),v.getInt("keep"),v.getBoolean("enabled"));
                for(String node:List.of(route.hub(),route.source(),route.target()))if(!nodes.containsKey(node)||nodes.get(node).kind()!=Kind.BUILDING)throw new IOException("Маршрут ссылается на неверное здание");routes.put(id,route);}
            towns.put(town,new Network(town,nodes,links,routes));
        }
        Map<UUID,CourierJob> jobs=new HashMap<>();var jj=c.getConfigurationSection("jobs");for(String raw:jj.getKeys(false)){
            var j=required(jj,raw);UUID id=UUID.fromString(raw),town=UUID.fromString(j.getString("town",""));String route=j.getString("route");
            if(!towns.containsKey(town)||!towns.get(town).routes().containsKey(route))throw new IOException("Курьер потерял маршрут");
            var job=new CourierJob(id,town,route,j.getString("hub"),j.getString("source"),j.getString("target"),j.getInt("level"),positions(j,"outbound"),positions(j,"delivery"),positions(j,"home"),
                CourierJob.Phase.valueOf(j.getString("phase","")),j.getInt("waypoint"),Position.decode(j.getString("position","")),j.getInt("handling"));
            if(job.waypoint()>job.path().size())throw new IOException("Повреждён прогресс курьера");jobs.put(id,job);
        }
        last=new Snapshot(towns,jobs);return last;
    }
    private ConfigurationSection required(ConfigurationSection c,String key)throws IOException{var value=c.getConfigurationSection(key);if(value==null)throw new IOException("Нет раздела "+key);return value;}
    private List<Position> positions(ConfigurationSection c,String key){return c.getStringList(key).stream().map(Position::decode).toList();}
    public void save(Map<UUID,Network> networks,Map<UUID,CourierJob> jobs)throws IOException{
        var proposed=new Snapshot(networks,jobs);if(proposed.equals(last))return;
        var c=new YamlConfiguration();c.set("schema",1);c.createSection("towns");c.createSection("jobs");
        for(var n:networks.values()){
            String p="towns."+n.town()+".";c.createSection(p+"nodes");c.createSection(p+"routes");
            for(var node:n.nodes().values()){String k=p+"nodes."+node.id()+".";c.set(k+"building",node.project());c.set(k+"kind",node.kind().name());c.set(k+"point",node.point().encode());}
            c.set(p+"links",n.links().stream().map(l->l.a()+":"+l.b()).sorted().toList());
            for(var route:n.routes().values()){String k=p+"routes."+route.id()+".";c.set(k+"hub",route.hub());c.set(k+"source",route.source());c.set(k+"target",route.target());c.set(k+"filter",route.filter());c.set(k+"limit",route.limit());c.set(k+"keep",route.keep());c.set(k+"enabled",route.enabled());}
        }
        for(var j:jobs.values()){String k="jobs."+j.id()+".";c.set(k+"town",j.town().toString());c.set(k+"route",j.route());c.set(k+"hub",j.hub());c.set(k+"source",j.source());c.set(k+"target",j.target());c.set(k+"level",j.level());
            c.set(k+"outbound",j.outbound().stream().map(Position::encode).toList());c.set(k+"delivery",j.delivery().stream().map(Position::encode).toList());c.set(k+"home",j.home().stream().map(Position::encode).toList());
            c.set(k+"phase",j.phase().name());c.set(k+"waypoint",j.waypoint());c.set(k+"position",j.position().encode());c.set(k+"handling",j.handlingTicks());}
        var target=file.toAbsolutePath();Files.createDirectories(target.getParent());var tmp=Files.createTempFile(target.getParent(),"logistics-",".tmp");
        try{c.save(tmp.toFile());try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}}
        finally{Files.deleteIfExists(tmp);}
        last=proposed;
    }
}
