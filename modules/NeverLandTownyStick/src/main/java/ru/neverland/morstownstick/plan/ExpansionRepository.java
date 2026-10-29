package ru.neverland.morstownstick.plan;

import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;
import ru.neverland.morstownstick.model.CellKey;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

/** One bounded writer. A caller may use a plan only after both snapshot and audit ACK. */
public final class ExpansionRepository {
    public record Snapshot(Map<UUID,ExpansionPlan> plans){
        public Snapshot{plans=Map.copyOf(plans);if(plans.size()>2048)throw new IllegalArgumentException("Архив достиг предела 2048 планов");var towns=new HashSet<UUID>();for(var e:plans.entrySet()){if(!e.getKey().equals(e.getValue().id()))throw new IllegalArgumentException("Неверный ID плана");if(e.getValue().open()&&!towns.add(e.getValue().town()))throw new IllegalArgumentException("Два открытых плана одного города");}}
        public Snapshot with(ExpansionPlan plan){var next=new LinkedHashMap<>(plans);next.put(plan.id(),plan);return new Snapshot(next);}
    }
    private final Path file;private final Predicate<AuditRecord> audit;private final ThreadPoolExecutor worker;
    private final CompletableFuture<Void> terminated=new CompletableFuture<>();
    private volatile Snapshot visible=new Snapshot(Map.of());private volatile boolean ready,busy,failed,closing;
    public ExpansionRepository(Path file,Predicate<AuditRecord> audit){this.file=file;this.audit=audit;worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(2),r->{var t=new Thread(r,"NeverLand-expansion-file");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){terminated.complete(null);}};}
    public Snapshot state(){return visible;}public boolean ready(){return ready&&!failed&&!closing;}public boolean busy(){return busy;}
    public CompletionStage<Snapshot> load(){try(var guard=SuiteState.guard()){
        if(ready||busy||closing)throw new IllegalStateException("Хранилище уже открывается");busy=true;
        return CompletableFuture.supplyAsync(()->{try{if(Files.notExists(file))return new Snapshot(Map.of());if(Files.size(file)>8*1024*1024)throw new IOException("База планов превышает 8 МиБ");var yaml=new YamlConfiguration();yaml.load(file.toFile());return decode(yaml);}catch(Exception e){throw new CompletionException(e);}},worker).handle((value,error)->{try(var locked=SuiteState.guard()){busy=false;if(error!=null){failed=true;throw new CompletionException(error);}visible=value;ready=true;AtomicFiles.loaded(file);return value;}});
    }}
    public CompletionStage<Snapshot> commit(Snapshot expected,Snapshot next,AuditRecord record){try(var guard=SuiteState.guard()){
        if(!ready()||busy)return CompletableFuture.failedFuture(new IllegalStateException("Планы ещё загружаются, сохраняются или требуют проверки администратора"));
        if(!visible.equals(expected))return CompletableFuture.failedFuture(new IllegalStateException("План изменился; откройте его заново"));busy=true;
        return CompletableFuture.supplyAsync(()->{try{AtomicFiles.writeDetached(file,encode(next));forceDirectory(file.getParent());if(record!=null&&!audit.test(record))throw new IOException("План сохранён, но журнал требует сверки. Запуск запрещён");return next;}catch(Exception e){throw new CompletionException(e);}},worker).handle((value,error)->{try(var locked=SuiteState.guard()){busy=false;if(error!=null){failed=true;throw new CompletionException(error);}visible=value;return value;}});
    }}
    private static void forceDirectory(Path path)throws IOException{Path dir=path.toAbsolutePath();try(var c=FileChannel.open(dir,StandardOpenOption.READ)){c.force(true);}if(dir.getParent()!=null)try(var c=FileChannel.open(dir.getParent(),StandardOpenOption.READ)){c.force(true);}}
    public CompletionStage<Void> prepareClose(){try(var guard=SuiteState.guard()){closing=true;worker.shutdown();return terminated;}}
    public void awaitClosed(){var future=prepareClose().toCompletableFuture();if(SuiteState.held()&&!future.isDone())throw new IllegalStateException("Ожидайте сохранение планов вне общей блокировки");try{future.get(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}catch(Exception e){throw new IllegalStateException("Запись планов ещё завершается",e);}}
    public static String encode(Snapshot state)throws IOException{
        var y=new YamlConfiguration();y.set("schema",1);y.createSection("plans");
        for(var p:new TreeMap<>(state.plans()).values()){String k="plans."+p.id()+".";y.set(k+"town",p.town().toString());y.set(k+"author",p.author().toString());y.set(k+"mayor",p.mayor().toString());y.set(k+"revision",p.revision());y.set(k+"created",p.created());y.set(k+"updated",p.updated());y.set(k+"estimate",p.estimate());y.set(k+"economy",p.economy());y.set(k+"town-size",p.townSize());y.set(k+"status",p.status().name());y.set(k+"approved-by",p.approvedBy()==null?"":p.approvedBy().toString());y.set(k+"note",p.note());y.set(k+"cells",p.cells().stream().map(ExpansionRepository::cell).toList());y.set(k+"observed-owned",p.observedOwned().stream().map(ExpansionRepository::cell).toList());}
        String encoded=y.saveToString();if(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>8*1024*1024)throw new IOException("База планов превышает 8 МиБ");return encoded;
    }
    public static Snapshot decode(YamlConfiguration y)throws IOException{
        if(y.getInt("schema")!=1||y.getConfigurationSection("plans")==null)throw new IOException("Повреждена база планов");
        try{var plans=new LinkedHashMap<UUID,ExpansionPlan>();for(String raw:y.getConfigurationSection("plans").getKeys(false)){String k="plans."+raw+".";UUID id=UUID.fromString(raw);validateRow(y,k);String approval=y.getString(k+"approved-by","");var p=new ExpansionPlan(id,UUID.fromString(y.getString(k+"town","")),UUID.fromString(y.getString(k+"author","")),UUID.fromString(y.getString(k+"mayor","")),y.getInt(k+"revision"),y.getLong(k+"created"),y.getLong(k+"updated"),cells(y.getStringList(k+"cells")),y.getDouble(k+"estimate",Double.NaN),y.getBoolean(k+"economy"),y.getInt(k+"town-size",-1),ExpansionPlan.Status.valueOf(y.getString(k+"status","")),approval.isEmpty()?null:UUID.fromString(approval),cells(y.getStringList(k+"observed-owned")),y.getString(k+"note",""));plans.put(id,p);}return new Snapshot(plans);}catch(RuntimeException e){throw new IOException("Некорректная запись плана; исходный файл сохранён",e);}
    }
    private static void validateRow(YamlConfiguration y,String k){
        for(String key:List.of("town","author","mayor","status","approved-by","note"))if(!(y.get(k+key) instanceof String))throw new IllegalArgumentException("Отсутствует строка "+key);
        for(String key:List.of("revision","created","updated","town-size")){Object value=y.get(k+key);if(!(value instanceof Integer)&&!(value instanceof Long))throw new IllegalArgumentException("Отсутствует целое число "+key);if((key.equals("revision")||key.equals("town-size"))&&((Number)value).longValue()>Integer.MAX_VALUE)throw new IllegalArgumentException("Слишком большое число "+key);}
        if(!(y.get(k+"estimate") instanceof Number)||!(y.get(k+"economy") instanceof Boolean))throw new IllegalArgumentException("Повреждены условия сметы");
        for(String key:List.of("cells","observed-owned")){Object value=y.get(k+key);if(!(value instanceof List<?> values)||values.size()>64||values.stream().anyMatch(v->!(v instanceof String)))throw new IllegalArgumentException("Повреждены участки плана");}
    }
    private static String cell(CellKey c){return Base64.getEncoder().encodeToString(c.world().getBytes(java.nio.charset.StandardCharsets.UTF_8))+":"+c.x()+":"+c.z();}
    private static List<CellKey> cells(List<String> values){return values.stream().map(v->{var p=v.split(":",-1);if(p.length!=3)throw new IllegalArgumentException("Некорректный участок");return new CellKey(new String(Base64.getDecoder().decode(p[0]),java.nio.charset.StandardCharsets.UTF_8),Integer.parseInt(p[1]),Integer.parseInt(p[2]));}).toList();}
}
