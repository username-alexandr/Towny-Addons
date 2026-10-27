package ru.neverland.townysieges;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** One bounded SQLite worker. No Bukkit, player or world calls on this thread. */
public final class RaidStore implements AutoCloseable {
    public record Key(UUID world,int x,int y,int z) { public String id(){return world+":"+x+":"+y+":"+z;} }
    public record Raid(UUID id,UUID attacker,UUID defender,UUID actor,long created,long starts,long ends,long next,String state) {
        public boolean live(long now){return state.equals("OPEN")&&now<ends;}
        public boolean fighting(long now){return live(now)&&now>=starts;}
    }
    public record Hit(Key key,UUID town,String material,long generation,double max,double damage){}
    public record Result(Hit hit,double hp,String receipt){}
    public record Health(double hp,double maximum,long lastHit){}
    public record Audit(String actor,String attacker,String defender,String action,long time,int blocks){}
    private final ThreadPoolExecutor worker;
    private final Consumer<Throwable> failure;
    private volatile boolean healthy,closed;
    private Connection db;
    private final CompletableFuture<Void> ready;
    private final CompletableFuture<Void> stopped=new CompletableFuture<>();
    @FunctionalInterface private interface Sql<T>{T run()throws Exception;}
    public RaidStore(Path file,Consumer<Throwable> failure) {
        this.failure=failure;
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(512),r->{var t=new Thread(r,"NeverLand-Raid-Storage");t.setDaemon(true);return t;}) {
            @Override protected void terminated(){try{if(db!=null)db.close();stopped.complete(null);}catch(SQLException error){stopped.completeExceptionally(error);failure.accept(error);}}
        };
        ready=submit(()->{
            Files.createDirectories(file.toAbsolutePath().getParent());
            Class.forName("org.sqlite.JDBC");db=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());
            try(var s=db.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");s.execute("PRAGMA synchronous=FULL");s.execute("PRAGMA busy_timeout=3000");
                s.execute("CREATE TABLE IF NOT EXISTS raids(id TEXT PRIMARY KEY,attacker TEXT NOT NULL,defender TEXT NOT NULL,actor TEXT NOT NULL,created INTEGER NOT NULL,starts INTEGER NOT NULL,ends INTEGER NOT NULL,next INTEGER NOT NULL,state TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS hp(key TEXT PRIMARY KEY,town TEXT NOT NULL,material TEXT NOT NULL,generation INTEGER NOT NULL,maximum REAL NOT NULL,hp REAL NOT NULL,last_hit INTEGER NOT NULL,receipt TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS impacts(blast TEXT NOT NULL,key TEXT NOT NULL,at INTEGER NOT NULL,PRIMARY KEY(blast,key))");
                s.execute("CREATE TABLE IF NOT EXISTS owners(key TEXT PRIMARY KEY,actor TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS audit(id INTEGER PRIMARY KEY AUTOINCREMENT,actor TEXT NOT NULL,attacker TEXT NOT NULL,defender TEXT NOT NULL,action TEXT NOT NULL,at INTEGER NOT NULL,blocks INTEGER NOT NULL)");
                s.execute("CREATE INDEX IF NOT EXISTS impacts_at ON impacts(at)");
                s.execute("CREATE INDEX IF NOT EXISTS audit_attacker ON audit(attacker,id)");
                s.execute("CREATE INDEX IF NOT EXISTS audit_defender ON audit(defender,id)");
                // Never replay a destructive callback after a restart. A surviving block needs a new real hit.
                s.execute("UPDATE hp SET hp=1,receipt='' WHERE receipt<>''");
            }
            healthy=true;return null;
        });
    }
    public boolean healthy(){return healthy&&!closed;}
    public CompletionStage<Void> ready(){return ready;}
    public CompletionStage<Void> stopped(){return stopped;}
    private <T> CompletableFuture<T> submit(Sql<T> work) {
        var result=new CompletableFuture<T>();
        if(closed){result.completeExceptionally(new IllegalStateException("Хранилище рейдов остановлено"));return result;}
        try{worker.execute(()->{try{result.complete(work.run());}catch(Throwable e){if(!(e instanceof IllegalArgumentException)){healthy=false;failure.accept(e);}result.completeExceptionally(e);}});}
        catch(RejectedExecutionException e){var error=new IllegalStateException("Очередь рейдов занята; разрушение остановлено до перезапуска",e);if(healthy){healthy=false;failure.accept(error);}result.completeExceptionally(error);}
        return result;
    }
    private <T> T transaction(Sql<T> work)throws Exception {
        db.setAutoCommit(false);try{T result=work.run();db.commit();return result;}
        catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
    public CompletionStage<List<Raid>> raids(){return submit(this::readRaids);}
    private List<Raid> readRaids()throws SQLException {
        var list=new ArrayList<Raid>();try(var s=db.createStatement();var r=s.executeQuery("SELECT * FROM raids")) {
            while(r.next())list.add(new Raid(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("attacker")),UUID.fromString(r.getString("defender")),UUID.fromString(r.getString("actor")),r.getLong("created"),r.getLong("starts"),r.getLong("ends"),r.getLong("next"),r.getString("state")));
        }return List.copyOf(list);
    }
    public CompletionStage<List<Raid>> start(Raid raid) {return submit(()->transaction(()->{
        try(var p=db.prepareStatement("DELETE FROM raids WHERE ends<=? AND next<=?")){p.setLong(1,raid.created());p.setLong(2,raid.created());p.executeUpdate();}
        for(Raid old:readRaids()) {
            if(old.live(raid.created())&&(old.attacker().equals(raid.attacker())||old.defender().equals(raid.attacker())||old.attacker().equals(raid.defender())||old.defender().equals(raid.defender())))throw new IllegalArgumentException("Один из городов уже участвует в рейде");
            if(old.defender().equals(raid.defender())&&old.next()>raid.created())throw new IllegalArgumentException("Город отдыхает после предыдущего рейда");
        }
        try(var p=db.prepareStatement("INSERT INTO raids VALUES(?,?,?,?,?,?,?,?,?)")) {
            p.setString(1,raid.id().toString());p.setString(2,raid.attacker().toString());p.setString(3,raid.defender().toString());p.setString(4,raid.actor().toString());p.setLong(5,raid.created());p.setLong(6,raid.starts());p.setLong(7,raid.ends());p.setLong(8,raid.next());p.setString(9,raid.state());p.executeUpdate();
        }
        audit(raid.actor().toString(),raid.attacker().toString(),raid.defender().toString(),"DECLARE",raid.created(),0);
        return readRaids();
    }));}
    public CompletionStage<List<Raid>> cancel(UUID town,String actor,long now){return submit(()->transaction(()->{
        try(var p=db.prepareStatement("UPDATE raids SET state='CANCELLED' WHERE (attacker=? OR defender=?) AND state='OPEN' AND ends>?")){p.setString(1,town.toString());p.setString(2,town.toString());p.setLong(3,now);p.executeUpdate();}
        audit(actor,"",town.toString(),"CANCEL",now,0);return readRaids();
    }));}
    public CompletionStage<Map<String,UUID>> owners(){return submit(()->{var out=new HashMap<String,UUID>();try(var s=db.createStatement();var r=s.executeQuery("SELECT * FROM owners")){while(r.next())out.put(r.getString(1),UUID.fromString(r.getString(2)));}return Map.copyOf(out);});}
    public CompletionStage<Set<String>> damagedKeys(){return submit(()->{var out=new HashSet<String>();try(var s=db.createStatement();var r=s.executeQuery("SELECT key FROM hp")){while(r.next())out.add(r.getString(1));}return Set.copyOf(out);});}
    public CompletionStage<Void> owner(Key key,UUID player){return submit(()->{
        try(var p=db.prepareStatement(player==null?"DELETE FROM owners WHERE key=?":"INSERT OR REPLACE INTO owners VALUES(?,?)")){p.setString(1,key.id());if(player!=null)p.setString(2,player.toString());p.executeUpdate();}return null;
    });}
    public CompletionStage<Void> reset(Key key){return submit(()->{try(var p=db.prepareStatement("DELETE FROM hp WHERE key=?")){p.setString(1,key.id());p.executeUpdate();}return null;});}
    public CompletionStage<List<Result>> damage(UUID blast,String actor,UUID attacker,UUID defender,long at,int repairSeconds,List<Hit> hits) {return submit(()->transaction(()->{
        if(hits.size()>6000)throw new IllegalArgumentException("Слишком много целей одного взрыва");
        var results=new ArrayList<Result>();
        for(Hit hit:hits) {
            if(!hit.town().equals(defender)||!Double.isFinite(hit.damage())||hit.damage()<=0||!Double.isFinite(hit.max())||hit.max()<=0)throw new IllegalArgumentException("Неверный урон блока");
            try(var p=db.prepareStatement("INSERT OR IGNORE INTO impacts VALUES(?,?,?)")){p.setString(1,blast.toString());p.setString(2,hit.key().id());p.setLong(3,at);if(p.executeUpdate()==0)continue;}
            double old=hit.max();String pending="";
            try(var p=db.prepareStatement("SELECT * FROM hp WHERE key=?")) {
                p.setString(1,hit.key().id());try(var r=p.executeQuery()) {
                    if(r.next()&&r.getString("town").equals(defender.toString())&&r.getString("material").equals(hit.material())&&at-r.getLong("last_hit")<repairSeconds*1000L) {
                        double stored=r.getDouble("hp");if(!Double.isFinite(stored)||stored<0||stored>1_000_000)throw new SQLException("Повреждена прочность блока");
                        old=Math.min(hit.max(),stored);pending=r.getString("receipt");
                    }
                }
            }
            if(!pending.isEmpty())continue;
            double hp=Math.max(0,old-hit.damage());String receipt=hp==0?UUID.randomUUID().toString():"";
            try(var p=db.prepareStatement("INSERT OR REPLACE INTO hp VALUES(?,?,?,?,?,?,?,?)")) {
                p.setString(1,hit.key().id());p.setString(2,defender.toString());p.setString(3,hit.material());p.setLong(4,hit.generation());p.setDouble(5,hit.max());p.setDouble(6,hp);p.setLong(7,at);p.setString(8,receipt);p.executeUpdate();
            }
            results.add(new Result(hit,hp,receipt));
        }
        if(!results.isEmpty())audit(actor,attacker.toString(),defender.toString(),"TNT_HIT",at,results.size());
        try(var p=db.prepareStatement("DELETE FROM impacts WHERE at<?")){p.setLong(1,at-86400000L);p.executeUpdate();}
        return List.copyOf(results);
    }));}
    public CompletionStage<Void> finish(Result result,boolean destroyed){return submit(()->{
        try(var p=db.prepareStatement(destroyed?"DELETE FROM hp WHERE key=? AND receipt=?":"UPDATE hp SET hp=1,receipt='' WHERE key=? AND receipt=?")) {
            p.setString(1,result.hit().key().id());p.setString(2,result.receipt());p.executeUpdate();
        }return null;
    });}
    public CompletionStage<Health> health(Key key,String material,double max,long now,int repair){return submit(()->{
        try(var p=db.prepareStatement("SELECT * FROM hp WHERE key=?")){p.setString(1,key.id());try(var r=p.executeQuery()){if(r.next()&&r.getString("material").equals(material)&&now-r.getLong("last_hit")<repair*1000L){double hp=r.getDouble("hp");if(!Double.isFinite(hp)||hp<0||hp>1_000_000)throw new SQLException("Повреждена прочность блока");return new Health(Math.min(max,hp),max,r.getLong("last_hit"));}}}
        return new Health(max,max,0);
    });}
    private void audit(String actor,String attacker,String defender,String action,long now,int blocks)throws SQLException {
        try(var p=db.prepareStatement("INSERT INTO audit(actor,attacker,defender,action,at,blocks) VALUES(?,?,?,?,?,?)")){p.setString(1,actor);p.setString(2,attacker);p.setString(3,defender);p.setString(4,action);p.setLong(5,now);p.setInt(6,blocks);p.executeUpdate();}
    }
    public CompletionStage<List<Audit>> history(UUID town){return submit(()->{var out=new ArrayList<Audit>();try(var p=db.prepareStatement("SELECT * FROM audit WHERE attacker=? OR defender=? ORDER BY id DESC LIMIT 10")){p.setString(1,town.toString());p.setString(2,town.toString());try(var r=p.executeQuery()){while(r.next())out.add(new Audit(r.getString("actor"),r.getString("attacker"),r.getString("defender"),r.getString("action"),r.getLong("at"),r.getInt("blocks")));}}return List.copyOf(out);});}
    @Override public void close(){closed=true;healthy=false;worker.shutdown(); /* queued commits finish; connection closes on the worker */}
}
