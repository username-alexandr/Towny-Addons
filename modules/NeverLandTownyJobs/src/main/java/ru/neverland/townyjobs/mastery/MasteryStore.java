package ru.neverland.townyjobs.mastery;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.UnaryOperator;
import com.google.gson.Gson;
import ru.neverland.townyjobs.Profession;
import static ru.neverland.townyjobs.mastery.MasteryModel.*;

/** Only verified action proofs enter this bounded single SQLite writer. No Bukkit calls or bank calls. */
public final class MasteryStore implements AutoCloseable {
    private final Path file,marker;private final Gson gson=new Gson();private final ThreadPoolExecutor worker;
    private final CompletableFuture<Void> ready,closed=new CompletableFuture<>();
    private volatile Map<UUID,Worker> view=Map.of();private volatile boolean failed,closing;private Connection db;private long clock,actions;
    private interface Sql<T>{T run()throws Exception;}
    public MasteryStore(Path file){
        this.file=file;marker=file.resolveSibling("mastery.created");
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(128),r->{var t=new Thread(r,"NeverLand-mastery-write");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){
            @Override protected void terminated(){try{if(db!=null)db.close();closed.complete(null);}catch(SQLException error){failed=true;closed.completeExceptionally(error);}}
        };ready=submit(()->{open();return null;});
    }
    public CompletionStage<Void> ready(){return ready;}public CompletionStage<Void> closed(){return closed;}
    public boolean healthy(){return !failed&&!closing&&ready.isDone()&&!ready.isCompletedExceptionally();}
    public Worker get(UUID player){return view.getOrDefault(player,Worker.empty());}
    private <T>CompletableFuture<T> submit(Sql<T> action){
        var result=new CompletableFuture<T>();if(closing){result.completeExceptionally(new IOException("Мастерские заказы останавливаются"));return result;}
        try{worker.execute(()->{try{if(failed)throw new IOException("Мастерские заказы ожидают проверки хранилища");result.complete(action.run());}catch(Throwable error){if(error instanceof SQLException||error instanceof IOException)failed=true;result.completeExceptionally(error);}});}catch(RejectedExecutionException error){result.completeExceptionally(new IOException("Очередь мастерских заказов заполнена; новое действие не принято",error));}return result;
    }
    private <T>T transaction(Sql<T> action)throws Exception{db.setAutoCommit(false);try{T result=action.run();db.commit();return result;}catch(Throwable error){try{db.rollback();}catch(SQLException rollback){error.addSuppressed(rollback);}throw error;}finally{db.setAutoCommit(true);}}
    private void open()throws Exception{
        if(Files.exists(marker)&&(!Files.isRegularFile(file)||!Files.readString(marker).equals("neverland-mastery-sqlite-v1\n")))throw new IOException("Отсутствует или повреждена база открытых специализаций; восстановите mastery.db и mastery.created из копии");
        Files.createDirectories(file.toAbsolutePath().getParent());Class.forName("org.sqlite.JDBC");db=DriverManager.getConnection("jdbc:sqlite:"+file);
        try(var s=db.createStatement()){
            s.execute("PRAGMA busy_timeout=3000");s.execute("PRAGMA synchronous=FULL");try(var r=s.executeQuery("PRAGMA journal_mode=WAL")){if(!r.next()||!r.getString(1).equalsIgnoreCase("wal"))throw new IOException("Мастерским заказам требуется SQLite WAL");}
            try(var r=s.executeQuery("PRAGMA user_version")){if(!r.next()||r.getInt(1)>1)throw new IOException("Неизвестная версия мастерских заказов");}try(var r=s.executeQuery("PRAGMA quick_check")){if(!r.next()||!r.getString(1).equals("ok"))throw new IOException("Повреждена база мастерских заказов");}
            s.execute("CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY,v TEXT NOT NULL) STRICT");s.execute("CREATE TABLE IF NOT EXISTS workers(id TEXT PRIMARY KEY,payload TEXT NOT NULL) STRICT");
            s.execute("CREATE TABLE IF NOT EXISTS proofs(id TEXT PRIMARY KEY,identity TEXT NOT NULL,observed INTEGER NOT NULL CHECK(observed>=0)) STRICT");s.execute("CREATE INDEX IF NOT EXISTS mastery_proof_age ON proofs(observed)");s.execute("PRAGMA user_version=1");
        }
        try(var s=db.createStatement();var r=s.executeQuery("SELECT v FROM meta WHERE k='clock'")){if(r.next())clock=Long.parseLong(r.getString(1));}
        var loaded=new HashMap<UUID,Worker>();try(var s=db.createStatement();var r=s.executeQuery("SELECT * FROM workers")){while(r.next()){String raw=r.getString("payload");if(raw.length()>262144)throw new IOException("Слишком большая запись специализаций");Worker value;try{value=gson.fromJson(raw,Worker.class);}catch(RuntimeException bad){throw new IOException("Повреждены специализации жителя",bad);}if(value==null||loaded.put(UUID.fromString(r.getString("id")),value)!=null||loaded.size()>10000)throw new IOException("Неверная запись специализаций");}}
        if(!Files.exists(marker))ru.neverland.core.AtomicFiles.writeDetached(marker,"neverland-mastery-sqlite-v1\n");view=Map.copyOf(loaded);
    }
    private void write(UUID player,Worker value)throws Exception{
        String encoded=gson.toJson(value);if(encoded.length()>262144)throw new IllegalStateException("Слишком большая запись специализаций; прежние данные сохранены");
        try(var s=db.prepareStatement("INSERT INTO workers VALUES(?,?) ON CONFLICT(id) DO UPDATE SET payload=excluded.payload")){s.setString(1,player.toString());s.setString(2,encoded);s.executeUpdate();}
    }
    private void time()throws Exception{clock=Math.max(clock,System.currentTimeMillis());try(var s=db.prepareStatement("INSERT INTO meta VALUES('clock',?) ON CONFLICT(k) DO UPDATE SET v=excluded.v")){s.setString(1,Long.toString(clock));s.executeUpdate();}}
    private void publish(UUID player,Worker value){var next=new HashMap<>(view);next.put(player,value);view=Map.copyOf(next);}
    private CompletionStage<Worker> change(UUID player,UnaryOperator<Worker> operation){return submit(()->{
        if(!view.containsKey(player)&&view.size()>=10000)throw new IllegalStateException("Достигнут предел записей профессий");var value=operation.apply(get(player));transaction(()->{write(player,value);time();return null;});publish(player,value);return value;
    });}
    public CompletionStage<Worker> start(UUID player,UUID order,UUID town,Definition definition,long now){return change(player,w->{if(w.active()!=null&&w.active().id().equals(order)){if(!w.active().town().equals(town)||!w.active().definition().equals(definition))throw new IllegalArgumentException("Номер заказа занят другими условиями");return w;}return w.start(order,town,definition,now);});}
    public CompletionStage<Worker> cancel(UUID player,UUID expected){return change(player,w->w.cancel(expected));}
    public CompletionStage<Worker> choose(UUID player,Profession profession,String id){return change(player,w->w.choose(profession,id));}
    public CompletionStage<Boolean> action(Proof proof){return submit(()->{
        try(var s=db.prepareStatement("SELECT identity FROM proofs WHERE id=?")){s.setString(1,proof.id().toString());try(var r=s.executeQuery()){if(r.next()){if(!r.getString(1).equals(proof.identity()))throw new IllegalArgumentException("Подтверждение работы уже принадлежит другому действию");return false;}}}
        long now=Math.max(clock,System.currentTimeMillis());if(proof.at()>now+60000||now-proof.at()>600000)throw new IllegalArgumentException("Подтверждение работы устарело");
        var before=get(proof.player());if(before.active()==null)return false;var after=before.action(proof);boolean changed=!after.equals(before);
        transaction(()->{try(var s=db.prepareStatement("INSERT INTO proofs VALUES(?,?,?)")){s.setString(1,proof.id().toString());s.setString(2,proof.identity());s.setLong(3,now);s.executeUpdate();}if(changed)write(proof.player(),after);time();if(++actions%1024==0)try(var s=db.prepareStatement("DELETE FROM proofs WHERE id IN (SELECT id FROM proofs WHERE observed<? LIMIT 4096)")){s.setLong(1,Math.max(0,clock-86400000));s.executeUpdate();}return null;});
        if(changed)publish(proof.player(),after);return changed;
    });}
    @Override public void close(){closing=true;worker.shutdown();}
}
