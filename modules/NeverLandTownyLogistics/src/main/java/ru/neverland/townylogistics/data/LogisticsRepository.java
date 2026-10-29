package ru.neverland.townylogistics.data;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townylogistics.model.*;
import ru.neverland.townylogistics.model.Network.*;
import ru.neverland.core.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.io.IOException;

/** Only acknowledged snapshots are published. Runtime file work never holds the shared suite ledger. */
public final class LogisticsRepository {
    public record Snapshot(Map<UUID,Network> networks,Map<UUID,CourierJob> jobs){public Snapshot{networks=Map.copyOf(networks);jobs=Map.copyOf(jobs);}}
    private final Path file;private final AcknowledgedState<Snapshot> data;private volatile Snapshot visible=new Snapshot(Map.of(),Map.of());private volatile boolean ready;private boolean busy,closing,failed;
    private final ExecutorService worker;private final CompletableFuture<Void> terminated=new CompletableFuture<>();private CompletableFuture<Void> pending=CompletableFuture.completedFuture(null);private CompletionStage<Void> close;
    public LogisticsRepository(Path file){this.file=file;data=new AcknowledgedState<>(file,visible,LogisticsRepository::encode,"NeverLand-logistics-write");worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(2),r->{var t=new Thread(r,"NeverLand-logistics-file");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){terminated.complete(null);}};}
    public Snapshot state(){return visible;}public boolean ready(){return ready;}public boolean busy(){try(var guard=SuiteState.guard()){return busy||data.busy();}}public boolean writable(){try(var guard=SuiteState.guard()){return ready&&!closing&&!failed&&data.writable();}}
    public CompletionStage<Snapshot> loadAsync(){try(var guard=SuiteState.guard()){if(busy||ready||closing)return CompletableFuture.failedFuture(new IllegalStateException("База логистики уже используется"));busy=true;pending=new CompletableFuture<>();var done=pending;return CompletableFuture.supplyAsync(()->{try{return read();}catch(Exception e){throw new CompletionException(e);}},worker).handle((snapshot,error)->{try(var locked=SuiteState.guard()){try{if(error!=null){failed=true;throw new CompletionException(error);}data.loaded(snapshot);visible=snapshot;ready=true;return snapshot;}finally{busy=false;done.complete(null);}}});}}
    /** Kept for offline migration/tests. Runtime calls loadAsync. */
    public Snapshot load()throws Exception{if(SuiteState.held())throw new IllegalStateException("Загрузка под общей блокировкой запрещена");var snapshot=read();try(var guard=SuiteState.guard()){data.loaded(snapshot);visible=snapshot;ready=true;return snapshot;}}
    private Snapshot read()throws Exception{if(Files.notExists(file))return new Snapshot(Map.of(),Map.of());if(Files.size(file)>32*1024*1024)throw new IOException("База логистики слишком велика");return decode(SafeYaml.load(file));}
    public CompletionStage<Snapshot> commit(Snapshot before,Snapshot next){try(var guard=SuiteState.guard()){if(!writable()||busy)return CompletableFuture.failedFuture(new IOException("База логистики сохраняется или недоступна"));if(!visible.equals(before))return CompletableFuture.failedFuture(new IllegalStateException("Данные логистики изменились"));busy=true;pending=new CompletableFuture<>();var done=pending;return data.commit(before,next).thenCompose(saved->CompletableFuture.supplyAsync(()->{try{forceDirectory(file.toAbsolutePath().getParent());return saved;}catch(IOException error){throw new CompletionException(error);}},worker)).handle((saved,error)->{try(var locked=SuiteState.guard()){try{if(error!=null){failed=true;throw new CompletionException(error);}visible=saved;return saved;}finally{busy=false;done.complete(null);}}});}}
    /** Kept for old offline tests only, never waits while holding SuiteState. */
    public void save(Map<UUID,Network> networks,Map<UUID,CourierJob> jobs)throws IOException{if(SuiteState.held())throw new IllegalStateException("Сохраняйте базу асинхронно");try{commit(visible,new Snapshot(networks,jobs)).toCompletableFuture().get(10,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException(error);}catch(ExecutionException|TimeoutException error){throw new IOException(error);}}
    private static void forceDirectory(Path directory)throws IOException{if(directory==null)return;try(var c=FileChannel.open(directory,StandardOpenOption.READ)){c.force(true);}if(directory.getParent()!=null)try(var c=FileChannel.open(directory.getParent(),StandardOpenOption.READ)){c.force(true);}}
    public CompletionStage<Void> prepareClose(){try(var guard=SuiteState.guard()){if(close!=null)return close;closing=true;close=pending.handle((v,e)->null).thenCompose(v->data.prepareClose()).handle((v,e)->{worker.shutdown();return e;}).thenCompose(error->terminated.thenCompose(v->error==null?CompletableFuture.completedFuture(null):CompletableFuture.failedFuture(error)));return close;}}
    public void awaitClosed(){var done=prepareClose().toCompletableFuture();if(SuiteState.held()&&!done.isDone())throw new IllegalStateException("Ожидайте запись вне общей блокировки");try{done.get(10,TimeUnit.SECONDS);}catch(Exception error){throw new IllegalStateException("Запись логистики ещё завершается",error);}}
    public static Snapshot decode(ConfigurationSection c)throws IOException{
        if((c.getInt("schema")!=1&&c.getInt("schema")!=2)||c.getConfigurationSection("towns")==null||c.getConfigurationSection("jobs")==null)throw new IOException("Повреждена база логистики");
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
                CourierJob.Phase.valueOf(j.getString("phase","")),j.getInt("waypoint"),Position.decode(j.getString("position","")),j.getInt("handling"),j.getInt("detours",0),j.getString("recovery-note",""));
            if(job.waypoint()>job.path().size())throw new IOException("Повреждён прогресс курьера");jobs.put(id,job);
        }
        return new Snapshot(towns,jobs);
    }
    private static ConfigurationSection required(ConfigurationSection c,String key)throws IOException{var value=c.getConfigurationSection(key);if(value==null)throw new IOException("Нет раздела "+key);return value;}
    private static List<Position> positions(ConfigurationSection c,String key){return c.getStringList(key).stream().map(Position::decode).toList();}
    public static String encode(Snapshot snapshot)throws IOException{
        var c=new YamlConfiguration();c.set("schema",2);c.createSection("towns");c.createSection("jobs");
        for(var n:new TreeMap<>(snapshot.networks()).values()){
            String p="towns."+n.town()+".";c.createSection(p+"nodes");c.createSection(p+"routes");
            for(var node:n.nodes().values()){String k=p+"nodes."+node.id()+".";c.set(k+"building",node.project());c.set(k+"kind",node.kind().name());c.set(k+"point",node.point().encode());}
            c.set(p+"links",n.links().stream().map(l->l.a()+":"+l.b()).sorted().toList());
            for(var route:n.routes().values()){String k=p+"routes."+route.id()+".";c.set(k+"hub",route.hub());c.set(k+"source",route.source());c.set(k+"target",route.target());c.set(k+"filter",route.filter());c.set(k+"limit",route.limit());c.set(k+"keep",route.keep());c.set(k+"enabled",route.enabled());}
        }
        for(var j:new TreeMap<>(snapshot.jobs()).values()){String k="jobs."+j.id()+".";c.set(k+"town",j.town().toString());c.set(k+"route",j.route());c.set(k+"hub",j.hub());c.set(k+"source",j.source());c.set(k+"target",j.target());c.set(k+"level",j.level());
            c.set(k+"outbound",j.outbound().stream().map(Position::encode).toList());c.set(k+"delivery",j.delivery().stream().map(Position::encode).toList());c.set(k+"home",j.home().stream().map(Position::encode).toList());
            c.set(k+"phase",j.phase().name());c.set(k+"waypoint",j.waypoint());c.set(k+"position",j.position().encode());c.set(k+"handling",j.handlingTicks());c.set(k+"detours",j.detours());c.set(k+"recovery-note",j.recoveryNote());}
        String value=c.saveToString();if(value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32*1024*1024)throw new IOException("База логистики достигла предела");return value;
    }
}
