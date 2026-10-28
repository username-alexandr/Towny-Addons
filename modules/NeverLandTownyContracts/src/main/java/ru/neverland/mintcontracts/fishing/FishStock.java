package ru.neverland.mintcontracts.fishing;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** The database worker never reads Bukkit state. Overlapping circles have no chunk-edge reset. */
public final class FishStock implements AutoCloseable {
    public record Point(UUID world,int x,int z){public Point{Objects.requireNonNull(world);}}
    public record Decision(UUID id,Point point,UUID player,String state,int remaining,long retryAt){public boolean reserved(){return state.equals("RESERVED")||state.equals("CAUGHT");}}
    private record Debt(int x,int z,long created,long expires){}
    @FunctionalInterface private interface Sql<T>{T run()throws Exception;}
    private final Path file;private final ThreadPoolExecutor worker;private final CompletableFuture<Void> ready;private Connection connection;
    private volatile boolean failed,closing;
    private final CompletableFuture<Void> closed=new CompletableFuture<>();
    public FishStock(Path file){
        this.file=file;
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),r->{Thread t=new Thread(r,"NeverLand-fish-stock");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){
            @Override protected void terminated(){try{if(connection!=null)connection.close();closed.complete(null);}catch(SQLException e){failed=true;closed.completeExceptionally(e);}}
        };
        ready=submit(()->{open();return null;});
    }
    public CompletionStage<Void> ready(){return ready;}
    public CompletionStage<Void> closed(){return closed;}
    public boolean available(){return !failed&&!closing&&ready.isDone()&&!ready.isCompletedExceptionally()&&worker.getQueue().remainingCapacity()>0;}
    private <T>CompletableFuture<T> submit(Sql<T> action){
        var f=new CompletableFuture<T>();if(closing){f.completeExceptionally(new IOException("Хранилище рыбы останавливается"));return f;}
        try{worker.execute(()->{try{if(failed)throw new IOException("Хранилище рыбы требует проверки");f.complete(action.run());}catch(Throwable e){if(e instanceof SQLException||e instanceof IOException)failed=true;f.completeExceptionally(e);}});}
        catch(RejectedExecutionException busy){f.completeExceptionally(new IOException("Хранилище рыбы занято",busy));}return f;
    }
    private void open()throws Exception{
        Files.createDirectories(file.toAbsolutePath().getParent());Class.forName("org.sqlite.JDBC");connection=DriverManager.getConnection("jdbc:sqlite:"+file);
        try(var s=connection.createStatement()){
            s.execute("PRAGMA busy_timeout=3000");s.execute("PRAGMA synchronous=FULL");
            try(var r=s.executeQuery("PRAGMA journal_mode=WAL")){if(!r.next()||!r.getString(1).equalsIgnoreCase("wal"))throw new IOException("Хранилище рыбы требует SQLite WAL");}
            try(var r=s.executeQuery("PRAGMA user_version")){if(!r.next()||r.getInt(1)>1)throw new IOException("Неизвестная версия базы рыбы");}
            try(var r=s.executeQuery("PRAGMA quick_check")){if(!r.next()||!r.getString(1).equals("ok"))throw new IOException("Повреждена база рыбы");}
            s.execute("CREATE TABLE IF NOT EXISTS clock (id INTEGER PRIMARY KEY CHECK(id=1),now INTEGER NOT NULL CHECK(now>=0))");
            s.execute("CREATE TABLE IF NOT EXISTS catches (id TEXT PRIMARY KEY,world TEXT NOT NULL,x INTEGER NOT NULL,z INTEGER NOT NULL,player TEXT NOT NULL,created INTEGER NOT NULL,expires INTEGER NOT NULL CHECK(expires>created),state TEXT NOT NULL CHECK(state IN ('RESERVED','CAUGHT','RELEASED','DEPLETED')),remaining INTEGER NOT NULL,retry_at INTEGER NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS fish_spatial ON catches(world,x,z,expires)");s.execute("CREATE INDEX IF NOT EXISTS fish_expiry ON catches(expires)");s.execute("PRAGMA user_version=1");
        }
    }
    private <T>T transaction(Sql<T> action)throws Exception{
        connection.setAutoCommit(false);try{var result=action.run();connection.commit();return result;}catch(Throwable e){try{connection.rollback();}catch(SQLException more){e.addSuppressed(more);}throw e;}finally{connection.setAutoCommit(true);}
    }
    private long now(long clock)throws SQLException{
        if(clock<1)throw new IllegalArgumentException("Некорректное время рыбалки");long last=0;
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT now FROM clock WHERE id=1")){if(r.next())last=r.getLong(1);}
        long value=Math.max(last,clock);try(var s=connection.prepareStatement("INSERT INTO clock(id,now) VALUES(1,?) ON CONFLICT(id) DO UPDATE SET now=excluded.now")){s.setLong(1,value);s.executeUpdate();}return value;
    }
    private Decision get(UUID id)throws SQLException{
        try(var s=connection.prepareStatement("SELECT * FROM catches WHERE id=?")){s.setString(1,id.toString());try(var r=s.executeQuery()){return r.next()?new Decision(id,new Point(UUID.fromString(r.getString("world")),r.getInt("x"),r.getInt("z")),UUID.fromString(r.getString("player")),r.getString("state"),r.getInt("remaining"),r.getLong("retry_at")):null;}}
    }
    private List<Debt> debts(Point p,int radius,long now)throws Exception{
        var out=new ArrayList<Debt>();long square=(long)radius*radius;
        try(var s=connection.prepareStatement("SELECT x,z,created,expires FROM catches WHERE world=? AND x BETWEEN ? AND ? AND z BETWEEN ? AND ? AND expires>? AND state IN ('RESERVED','CAUGHT')")){
            s.setString(1,p.world().toString());s.setLong(2,(long)p.x()-radius);s.setLong(3,(long)p.x()+radius);s.setLong(4,(long)p.z()-radius);s.setLong(5,(long)p.z()+radius);s.setLong(6,now);
            try(var r=s.executeQuery()){while(r.next()){long dx=(long)r.getInt(1)-p.x(),dz=(long)r.getInt(2)-p.z();if(dx*dx+dz*dz<=square)out.add(new Debt(r.getInt(1),r.getInt(2),r.getLong(3),r.getLong(4)));if(out.size()>100000)throw new IOException("Слишком много записей рыбалки на одном участке");}}
        }return out;
    }
    private static long debt(List<Debt> rows,long now){long total=0;for(var d:rows){long left=Math.max(0,d.expires()-Math.max(now,d.created()));total=Math.addExact(total,(left*1000+(d.expires()-d.created())-1)/(d.expires()-d.created()));}return total;}
    private static long retry(List<Debt> rows,long now,int capacity){
        long low=now,high=rows.stream().mapToLong(Debt::expires).max().orElse(now);while(low<high){long mid=low+(high-low)/2;if(debt(rows,mid)+1000<=(long)capacity*1000)high=mid;else low=mid+1;}return low;
    }
    public CompletionStage<Decision> reserve(UUID id,UUID player,Point p,FishingSettings settings,long wallTime){
        Objects.requireNonNull(id);Objects.requireNonNull(player);Objects.requireNonNull(p);Objects.requireNonNull(settings);
        return submit(()->transaction(()->{
            var old=get(id);if(old!=null){if(!old.point().equals(p)||!old.player().equals(player))throw new IllegalArgumentException("Попытка рыбалки уже принадлежит другому месту или игроку");return old;}
            long now=now(wallTime);var rows=debts(p,settings.radius(),now);long used=debt(rows,now);boolean fits=used+1000<=(long)settings.capacity()*1000;
            var next=new Decision(id,p,player,fits?"RESERVED":"DEPLETED",Math.max(0,(int)(((long)settings.capacity()*1000-used-(fits?1000:0))/1000)),fits?now:retry(rows,now,settings.capacity()));
            try(var s=connection.prepareStatement("INSERT INTO catches(id,world,x,z,player,created,expires,state,remaining,retry_at) VALUES(?,?,?,?,?,?,?,?,?,?)")){
                s.setString(1,id.toString());s.setString(2,p.world().toString());s.setInt(3,p.x());s.setInt(4,p.z());s.setString(5,player.toString());s.setLong(6,now);s.setLong(7,Math.addExact(now,settings.recoveryMillis()));s.setString(8,next.state());s.setInt(9,next.remaining());s.setLong(10,next.retryAt());s.executeUpdate();
            }return next;
        }));
    }
    public CompletionStage<Decision> finish(UUID id,boolean caught){return submit(()->transaction(()->{
        var row=get(id);if(row==null)throw new IllegalArgumentException("Попытка рыбалки не найдена");
        if(row.state().equals("RESERVED"))try(var s=connection.prepareStatement("UPDATE catches SET state=? WHERE id=? AND state='RESERVED'")){s.setString(1,caught?"CAUGHT":"RELEASED");s.setString(2,id.toString());s.executeUpdate();}
        return get(id);
    }));}
    public CompletionStage<Decision> inspect(UUID player,Point point,FishingSettings settings,long wallTime){return submit(()->{
        long now=now(wallTime);var rows=debts(point,settings.radius(),now);long used=debt(rows,now);return new Decision(new UUID(0,0),point,player,"STATUS",Math.max(0,(int)(((long)settings.capacity()*1000-used)/1000)),retry(rows,now,settings.capacity()));
    });}
    /** Old receipts remain for replay protection; compact only after the configured retention horizon. */
    public CompletionStage<Integer> compact(long olderThan){return submit(()->{try(var s=connection.prepareStatement("DELETE FROM catches WHERE expires<? AND expires<(SELECT now FROM clock WHERE id=1)")){s.setLong(1,olderThan);return s.executeUpdate();}});}
    @Override public void close(){
        closing=true;worker.shutdown();
    }
}
