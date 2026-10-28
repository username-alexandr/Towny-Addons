package ru.neverland.townytaxes.data;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import com.google.gson.Gson;
import ru.neverland.townytaxes.model.*;
import static ru.neverland.townytaxes.model.TaxPayment.Phase.*;

/** Single bounded SQLite worker. Durable phases, internal reserve and debts share one transaction. No Bukkit access. */
public final class TaxStore implements TaxPayments.Store,AutoCloseable {
    private static final int RECENT=5000,MAX_PENDING=4096;
    private static final String TERMINAL="'DONE','REFUNDED','REJECTED','VOID'";
    public record Snapshot(Map<UUID,TaxPayment> receipts,Map<UUID,Long> debts,long reserve){public Snapshot{receipts=Map.copyOf(receipts);debts=Map.copyOf(debts);valid(reserve);}}
    private final Path file;private final ThreadPoolExecutor worker;private final Gson gson=new Gson();private Connection db;
    private volatile Snapshot snapshot=new Snapshot(Map.of(),Map.of(),0);private volatile boolean failed,closing;private volatile String error="";
    private final CompletableFuture<Void> ready,closed=new CompletableFuture<>();
    interface Sql<T>{T run()throws Exception;}
    public TaxStore(Path file,long legacyReserve,Map<UUID,Long> legacyDebts,boolean required){
        this.file=file;valid(legacyReserve);legacyDebts=Map.copyOf(legacyDebts);legacyDebts.values().forEach(TaxStore::valid);var initialDebts=legacyDebts;
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),r->{var t=new Thread(r,"NeverLand-tax-payments");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){
            @Override protected void terminated(){try{if(db!=null)db.close();closed.complete(null);}catch(SQLException e){failed=true;closed.completeExceptionally(e);}}
        };
        ready=submit(()->{open(legacyReserve,initialDebts,required);return null;});
    }
    public CompletionStage<Void> ready(){return ready;}public CompletionStage<Void> closed(){return closed;}
    public boolean healthy(){return !failed&&!closing&&ready.isDone()&&!ready.isCompletedExceptionally();}public String error(){return error;}
    public Snapshot snapshot(){return snapshot;}
    @Override public TaxPayment get(UUID id){return snapshot.receipts().get(id);}
    private static long valid(long n){if(n<0||n>TaxCalculation.MAX_CENTS)throw new IllegalArgumentException("Превышен предел налогового учёта");return n;}
    private <T>CompletableFuture<T> submit(Sql<T> action){
        var future=new CompletableFuture<T>();if(closing){future.completeExceptionally(new IOException("Налоговый учёт останавливается"));return future;}
        try{worker.execute(()->{try{if(failed)throw new IOException("Налоговый учёт требует проверки");future.complete(action.run());}catch(Throwable e){if(e instanceof IOException||e instanceof SQLException){failed=true;error=e.getMessage();}future.completeExceptionally(e);}});}catch(RejectedExecutionException e){future.completeExceptionally(new IOException("Очередь налогов занята; деньги не списаны",e));}return future;
    }
    private <T>T transaction(Sql<T> action)throws Exception{db.setAutoCommit(false);try{T value=action.run();db.commit();return value;}catch(Throwable e){try{db.rollback();}catch(SQLException more){e.addSuppressed(more);}throw e;}finally{db.setAutoCommit(true);}}
    private void open(long reserve,Map<UUID,Long> debts,boolean required)throws Exception{
        if(required&&!Files.isRegularFile(file))throw new IOException("После миграции отсутствует payments.db; восстановите его из копии");
        Files.createDirectories(file.toAbsolutePath().getParent());Class.forName("org.sqlite.JDBC");db=DriverManager.getConnection("jdbc:sqlite:"+file);
        try(var s=db.createStatement()){
            s.execute("PRAGMA busy_timeout=3000");s.execute("PRAGMA synchronous=FULL");try(var r=s.executeQuery("PRAGMA journal_mode=WAL")){if(!r.next()||!r.getString(1).equalsIgnoreCase("wal"))throw new IOException("Налоговому учёту требуется SQLite WAL");}
            try(var r=s.executeQuery("PRAGMA user_version")){if(!r.next()||r.getInt(1)>1)throw new IOException("Неизвестная версия payments.db");}try(var r=s.executeQuery("PRAGMA quick_check")){if(!r.next()||!r.getString(1).equals("ok"))throw new IOException("Повреждён payments.db");}
            s.execute("CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY,v TEXT NOT NULL) STRICT");
            s.execute("CREATE TABLE IF NOT EXISTS money(k TEXT PRIMARY KEY,cents INTEGER NOT NULL CHECK(cents>=0 AND cents<=9000000000000)) STRICT");
            s.execute("CREATE TABLE IF NOT EXISTS payments(id TEXT PRIMARY KEY,phase TEXT NOT NULL CHECK(phase IN ('PREPARED','DEBITING','DEBITED','CREDITING','REFUND_READY','REFUNDING','DONE','REFUNDED','REJECTED','VOID')),source_scope TEXT NOT NULL,source_id TEXT NOT NULL,created INTEGER NOT NULL CHECK(created>=0),payload TEXT NOT NULL) STRICT");
            s.execute("CREATE INDEX IF NOT EXISTS tax_subject ON payments(source_scope,source_id,phase)");s.execute("CREATE INDEX IF NOT EXISTS tax_history ON payments(created)");
        }
        boolean migrated;try(var s=db.prepareStatement("SELECT v FROM meta WHERE k='legacy-imported'");var r=s.executeQuery()){migrated=r.next();if(migrated&&!r.getString(1).equals("1"))throw new IOException("Неизвестный маркер переноса налогов");}
        if(!migrated){if(required)throw new IOException("В payments.db нет подтверждённого переноса прежних денег и долгов");transaction(()->{putMoney("server",reserve);for(var e:debts.entrySet())putMoney("debt/"+e.getKey(),e.getValue());try(var s=db.createStatement()){s.execute("INSERT INTO meta VALUES('legacy-imported','1')");s.execute("PRAGMA user_version=1");}return null;});}
        var balances=new HashMap<UUID,Long>();long bank=-1;try(var s=db.createStatement();var r=s.executeQuery("SELECT * FROM money")){int count=0;while(r.next()){String key=r.getString("k");long amount=valid(r.getLong("cents"));if(key.equals("server"))bank=amount;else if(key.startsWith("debt/"))balances.put(UUID.fromString(key.substring(5)),amount);else throw new IOException("Неизвестный налоговый счёт");if(++count>100000)throw new IOException("Слишком много налоговых счетов");}}
        if(bank<0)throw new IOException("Отсутствует серверный налоговый резерв");var receipts=new HashMap<UUID,TaxPayment>();
        try(var s=db.createStatement();var r=s.executeQuery("SELECT * FROM payments WHERE phase NOT IN ("+TERMINAL+")")){while(r.next()){var value=decode(r);receipts.put(value.id(),value);if(receipts.size()>MAX_PENDING)throw new IOException("Слишком много незавершённых налоговых переводов");}}
        try(var s=db.createStatement();var r=s.executeQuery("SELECT * FROM payments WHERE phase IN ("+TERMINAL+") ORDER BY created DESC LIMIT "+RECENT)){while(r.next()){var value=decode(r);receipts.put(value.id(),value);}}
        snapshot=new Snapshot(receipts,balances,bank);
    }
    private TaxPayment decode(ResultSet row)throws Exception{
        String raw=row.getString("payload");if(raw==null||raw.length()>16384)throw new IOException("Неверный размер налоговой квитанции");TaxPayment value;
        try{value=gson.fromJson(raw,TaxPayment.class);}catch(RuntimeException e){throw new IOException("Повреждена налоговая квитанция",e);}
        if(value==null||!value.id().toString().equals(row.getString("id"))||!value.phase().name().equals(row.getString("phase"))||!value.sourceScope().name().equals(row.getString("source_scope"))||!value.sourceId().toString().equals(row.getString("source_id"))||value.created()!=row.getLong("created"))throw new IOException("Налоговая квитанция не соответствует индексу");return value;
    }
    private TaxPayment read(UUID id)throws Exception{try(var s=db.prepareStatement("SELECT * FROM payments WHERE id=?")){s.setString(1,id.toString());try(var r=s.executeQuery()){return r.next()?decode(r):null;}}}
    public CompletionStage<TaxPayment> inspect(UUID id){return submit(()->read(id));}
    public CompletionStage<Boolean> background(java.util.function.BooleanSupplier action){return submit(action::getAsBoolean);}
    public CompletionStage<TaxPayment> create(TaxPayment value){return submit(()->{
        var old=read(value.id());if(old!=null){if(!sameTerms(old,value))throw new IllegalArgumentException("ID налоговой квитанции уже занят другими условиями");return old;}
        if(value.phase()!=PREPARED)throw new IllegalArgumentException("Новая квитанция должна ожидать оплаты");
        if(snapshot.receipts().values().stream().filter(r->!r.terminal()).count()>=MAX_PENDING)throw new IllegalStateException("Нужно завершить прежние налоговые переводы");
        transaction(()->{try(var s=db.prepareStatement("INSERT INTO payments VALUES(?,?,?,?,?,?)")){bind(s,value);s.executeUpdate();}return null;});publish(value,snapshot.debts(),snapshot.reserve());return value;
    });}
    private static boolean sameTerms(TaxPayment a,TaxPayment b){return new TaxPayment(a.id(),a.policy(),a.kind(),a.sourceScope(),a.sourceId(),a.sourceName(),a.destinationScope(),a.destinationId(),a.destinationName(),a.calculation(),b.phase(),b.created(),a.debtOnReject(),a.note()).equals(b);}
    private void bind(PreparedStatement s,TaxPayment value)throws SQLException{s.setString(1,value.id().toString());s.setString(2,value.phase().name());s.setString(3,value.sourceScope().name());s.setString(4,value.sourceId().toString());s.setLong(5,value.created());s.setString(6,gson.toJson(value));}
    @Override public CompletionStage<Boolean> transition(TaxPayment.Phase expected,TaxPayment next){return submit(()->{
        var old=read(next.id());if(old==null||old.phase()!=expected)return false;if(!old.phase(next.phase()).equals(next))throw new IllegalArgumentException("Попытка изменить условия налогового платежа");
        var debts=new HashMap<>(snapshot.debts());long reserve=snapshot.reserve();
        if(next.phase()==DONE&&next.destinationScope()==Domain.Scope.GLOBAL)reserve=valid(Math.addExact(reserve,next.calculation().cents()));
        if(next.phase()==DONE&&next.kind()==TaxPayment.Kind.DEBT)debts.put(next.sourceId(),Math.max(0,debts.getOrDefault(next.sourceId(),0L)-next.calculation().cents()));
        if(next.phase()==REJECTED&&next.kind()==TaxPayment.Kind.TAX&&next.debtOnReject())debts.put(next.sourceId(),valid(Math.addExact(debts.getOrDefault(next.sourceId(),0L),next.calculation().cents())));
        long bank=reserve;transaction(()->{
            try(var s=db.prepareStatement("UPDATE payments SET phase=?,payload=? WHERE id=? AND phase=?")){s.setString(1,next.phase().name());s.setString(2,gson.toJson(next));s.setString(3,next.id().toString());s.setString(4,expected.name());if(s.executeUpdate()!=1)throw new IOException("Налоговый платёж изменён вне очереди");}
            if(bank!=snapshot.reserve())putMoney("server",bank);if(!Objects.equals(debts.get(next.sourceId()),snapshot.debts().get(next.sourceId())))putMoney("debt/"+next.sourceId(),debts.getOrDefault(next.sourceId(),0L));return null;
        });publish(next,debts,bank);return true;
    });}
    private void putMoney(String key,long amount)throws SQLException{valid(amount);try(var s=db.prepareStatement("INSERT INTO money VALUES(?,?) ON CONFLICT(k) DO UPDATE SET cents=excluded.cents")){s.setString(1,key);s.setLong(2,amount);s.executeUpdate();}}
    private void publish(TaxPayment value,Map<UUID,Long> debts,long reserve){
        var receipts=new HashMap<>(snapshot.receipts());receipts.put(value.id(),value);var terminal=receipts.values().stream().filter(TaxPayment::terminal).sorted(Comparator.comparingLong(TaxPayment::created).reversed()).toList();for(int n=RECENT;n<terminal.size();n++)receipts.remove(terminal.get(n).id());snapshot=new Snapshot(receipts,debts,reserve);
    }
    @Override public void close(){closing=true;worker.shutdown();}
}
