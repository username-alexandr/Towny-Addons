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
    public record Hit(Key key,UUID town,String material,long generation,double max,double damage,String project){public Hit(Key key,UUID town,String material,long generation,double max,double damage){this(key,town,material,generation,max,damage,"");}public Hit{Objects.requireNonNull(project);}}
    public record Result(Hit hit,double hp,String receipt){}
    public record Health(double hp,double maximum,long lastHit){}
    public record Audit(String actor,String attacker,String defender,String action,long time,int blocks){}
    public record Objective(UUID raid,String kind,int required,int achieved){}
    private final ThreadPoolExecutor worker;
    private final Consumer<Throwable> failure;
    private volatile boolean healthy,closed;
    private Connection db;
    private final Object repairGate=new Object();
    private final java.util.concurrent.atomic.AtomicLong repairRevision=new java.util.concurrent.atomic.AtomicLong();
    private final CompletableFuture<Void> ready;
    private final CompletableFuture<Void> stopped=new CompletableFuture<>();
    @FunctionalInterface interface Sql<T>{T run()throws Exception;}
    Connection connection(){return db;}
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
                s.execute("CREATE TABLE IF NOT EXISTS raid_objectives(raid TEXT PRIMARY KEY,kind TEXT NOT NULL,required INTEGER NOT NULL)");
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
            BuildingDamageStore.initialize(db);
            RaidRepairsStore.initialize(db);
            RaidLootStore.initialize(db);
            RansomStore.initialize(db);
            RaidCamps.initialize(db);
            WarCaptureStore.initialize(db);
            transaction(()->{expireSql(System.currentTimeMillis());return null;});
            healthy=true;return null;
        });
    }
    public boolean healthy(){return healthy&&!closed;}
    public CompletionStage<Void> ready(){return ready;}
    public CompletionStage<Void> stopped(){return stopped;}
    <T> CompletableFuture<T> submit(Sql<T> work) {
        var result=new CompletableFuture<T>();
        if(closed){result.completeExceptionally(new IllegalStateException("Хранилище рейдов остановлено"));return result;}
        try{worker.execute(()->{try{result.complete(work.run());}catch(Throwable e){if(!(e instanceof IllegalArgumentException)){healthy=false;failure.accept(e);}result.completeExceptionally(e);}});}
        catch(RejectedExecutionException e){var error=new IllegalStateException("Очередь рейдов занята; разрушение остановлено до перезапуска",e);if(healthy){healthy=false;failure.accept(error);}result.completeExceptionally(error);}
        return result;
    }
    /** Enqueue and revision advance share a short gate; no SQL or waiting occurs under it. */
    <T> CompletableFuture<T> repairMutation(Sql<T> work){synchronized(repairGate){repairRevision.incrementAndGet();return submit(work);}}
    public long repairDebtRevision(){if(!healthy())throw new IllegalStateException("Учёт повреждений недоступен");return repairRevision.get();}
    public CompletionStage<Boolean> hasRepairDebt(UUID town,String project){
        if(town==null||project==null||project.isBlank()||project.length()>128)throw new IllegalArgumentException("Не указан проект повреждённого здания");
        synchronized(repairGate){
            if(!healthy())return CompletableFuture.failedFuture(new IllegalStateException("Учёт повреждений недоступен"));
            return submit(()->{
                if(!healthy())throw new IllegalArgumentException("Учёт повреждений недоступен");
                try(var p=db.prepareStatement("SELECT EXISTS(SELECT 1 FROM building_damage WHERE town=? AND project=?) OR EXISTS(SELECT 1 FROM building_repairs WHERE town=? AND project=? AND state NOT IN ('DONE','CANCELLED'))")){
                    p.setString(1,town.toString());p.setString(2,project);p.setString(3,town.toString());p.setString(4,project);try(var rows=p.executeQuery()){if(!rows.next())throw new SQLException("Нет ответа учёта повреждений");return rows.getBoolean(1);}
                }
            });
        }
    }
    <T> T transaction(Sql<T> work)throws Exception {
        db.setAutoCommit(false);try{T result=work.run();db.commit();return result;}
        catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
    public CompletionStage<List<Raid>> raids(){return submit(this::readRaids);}
    private List<Raid> readRaids()throws SQLException {
        var list=new ArrayList<Raid>();try(var s=db.createStatement();var r=s.executeQuery("SELECT * FROM raids")) {
            while(r.next())list.add(new Raid(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("attacker")),UUID.fromString(r.getString("defender")),UUID.fromString(r.getString("actor")),r.getLong("created"),r.getLong("starts"),r.getLong("ends"),r.getLong("next"),r.getString("state")));
        }return List.copyOf(list);
    }
    public CompletionStage<List<Raid>> start(Raid raid) {return start(raid,"LEGACY",0);}
    /** Objective is frozen in the same commit as declaration; old rows remain legacy raids. */
    public CompletionStage<List<Raid>> start(Raid raid,String objective,int required) {
        if(raid==null||!Set.of("LEGACY","CORE","CHUNKS").contains(objective)||required!=(objective.equals("CHUNKS")?Math.max(1,Math.min(16,required)):objective.equals("CORE")?1:0))throw new IllegalArgumentException("Неверная цель рейда");
        return submit(()->transaction(()->{
        expireSql(raid.created());
        try(var p=db.prepareStatement("DELETE FROM raid_objectives WHERE raid IN (SELECT id FROM raids WHERE ends<=? AND next<=?)")){p.setLong(1,raid.created());p.setLong(2,raid.created());p.executeUpdate();}
        try(var p=db.prepareStatement("DELETE FROM raids WHERE ends<=? AND next<=? AND state<>'OPEN'")){p.setLong(1,raid.created());p.setLong(2,raid.created());p.executeUpdate();}
        for(Raid old:readRaids()) {
            if(RansomStore.blocking(db,old.id())&&(old.attacker().equals(raid.attacker())||old.defender().equals(raid.attacker())||old.attacker().equals(raid.defender())||old.defender().equals(raid.defender())))throw new IllegalArgumentException("У одного из городов ещё идёт перевод выкупа");
            if(old.live(raid.created())&&(old.attacker().equals(raid.attacker())||old.defender().equals(raid.attacker())||old.attacker().equals(raid.defender())||old.defender().equals(raid.defender())))throw new IllegalArgumentException("Один из городов уже участвует в рейде");
            if(old.defender().equals(raid.defender())&&old.next()>raid.created())throw new IllegalArgumentException("Город отдыхает после предыдущего рейда");
        }
        try(var p=db.prepareStatement("INSERT INTO raids VALUES(?,?,?,?,?,?,?,?,?)")) {
            p.setString(1,raid.id().toString());p.setString(2,raid.attacker().toString());p.setString(3,raid.defender().toString());p.setString(4,raid.actor().toString());p.setLong(5,raid.created());p.setLong(6,raid.starts());p.setLong(7,raid.ends());p.setLong(8,raid.next());p.setString(9,raid.state());p.executeUpdate();
        }
        try(var p=db.prepareStatement("INSERT INTO raid_objectives(raid,kind,required) VALUES(?,?,?)")){
            p.setString(1,raid.id().toString());p.setString(2,objective);p.setInt(3,required);p.executeUpdate();
        }
        audit(raid.actor().toString(),raid.attacker().toString(),raid.defender().toString(),"DECLARE",raid.created(),0);
        return readRaids();
    }));}
    private Objective objectiveSql(UUID raid)throws SQLException{
        String kind="LEGACY";int required=0;
        try(var p=db.prepareStatement("SELECT kind,required FROM raid_objectives WHERE raid=?")){p.setString(1,raid.toString());try(var r=p.executeQuery()){if(r.next()){kind=r.getString(1);required=r.getInt(2);}}}
        int achieved=0;
        if(kind.equals("CHUNKS"))try(var p=db.prepareStatement("SELECT COUNT(*) FROM war_chunks WHERE raid=? AND state='CAPTURED'")){p.setString(1,raid.toString());try(var r=p.executeQuery()){if(r.next())achieved=r.getInt(1);}}
        if(kind.equals("CORE"))try(var p=db.prepareStatement("SELECT COUNT(*) FROM raid_loot WHERE raid=? AND state='DONE'")){p.setString(1,raid.toString());try(var r=p.executeQuery()){if(r.next())achieved=r.getInt(1);}}
        return new Objective(raid,kind,required,achieved);
    }
    public CompletionStage<Objective> objective(UUID raid){if(raid==null)throw new IllegalArgumentException("Рейд не указан");return submit(()->objectiveSql(raid));}
    /** Victory and release share the transaction, so a late capture cannot change the result. */
    public CompletionStage<List<Raid>> victory(UUID raid,UUID attacker,UUID actor,long now){
        if(raid==null||attacker==null||actor==null||now<0)throw new IllegalArgumentException("Неверная заявка о победе");
        return submit(()->transaction(()->{
            Raid previous=readRaids().stream().filter(r->r.id().equals(raid)).findFirst().orElseThrow(()->new IllegalArgumentException("Рейд не найден"));
            if(!previous.attacker().equals(attacker)||!previous.state().equals("OPEN")||!previous.fighting(now)||!previous.fighting(System.currentTimeMillis()))throw new IllegalArgumentException("Победу можно объявить только во время своего рейда");
            Objective goal=objectiveSql(raid);
            if(goal.kind().equals("LEGACY")||goal.required()<1||goal.achieved()<goal.required())throw new IllegalArgumentException("Цель рейда ещё не выполнена");
            concludeSql(raid,"VICTORY",actor.toString(),now);return readRaids();
        }));
    }
    /** Exact-ID completion is idempotent only for the same outcome. Cooldown remains unchanged. */
    public CompletionStage<List<Raid>> conclude(UUID raid,String outcome,String actor,long now){
        if(raid==null||actor==null||now<0||!Set.of("CANCELLED","EXPIRED").contains(outcome))throw new IllegalArgumentException("Неверное завершение рейда");
        return submit(()->transaction(()->{concludeSql(raid,outcome,actor,now);return readRaids();}));
    }
    private boolean concludeSql(UUID raid,String outcome,String actor,long now)throws Exception{
        Raid previous;try(var p=db.prepareStatement("SELECT * FROM raids WHERE id=?")){p.setString(1,raid.toString());try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Рейд не найден");previous=new Raid(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("attacker")),UUID.fromString(r.getString("defender")),UUID.fromString(r.getString("actor")),r.getLong("created"),r.getLong("starts"),r.getLong("ends"),r.getLong("next"),r.getString("state"));}}
        if(!previous.state().equals("OPEN")){if(!previous.state().equals(outcome))throw new IllegalArgumentException("Рейд уже завершён с другим результатом");return false;}
        if(RansomStore.blocking(db,raid)&&!outcome.equals("SURRENDERED")&&!(outcome.equals("CANCELLED")&&RansomStore.read(db,raid).state().equals("REFUNDED")))throw new IllegalArgumentException("Выкуп ожидает оплаты или сверки; рейд нельзя закрыть до завершения перевода");
        if(outcome.equals("EXPIRED")&&previous.ends()>now)throw new IllegalArgumentException("Срок рейда ещё не закончился");
        try(var p=db.prepareStatement("UPDATE raids SET state=? WHERE id=? AND state='OPEN'")){p.setString(1,outcome);p.setString(2,raid.toString());if(p.executeUpdate()!=1)throw new SQLException("Состояние рейда изменилось");}
        int released=WarCaptureStore.releaseWar(db,raid,actor,now,"Рейд завершён: "+outcome+"; временный контроль снят");
        audit(actor,previous.attacker().toString(),previous.defender().toString(),outcome,now,released);return true;
    }
    public CompletionStage<List<Raid>> cancel(UUID town,String actor,long now){return submit(()->transaction(()->{
        for(Raid raid:readRaids())if((raid.attacker().equals(town)||raid.defender().equals(town))&&raid.live(now))concludeSql(raid.id(),"CANCELLED",actor,now);
        expireSql(now);return readRaids();
    }));}
    /** Also repairs captures left behind by older versions after a committed cancellation. */
    private void expireSql(long now)throws Exception{
        for(Raid raid:readRaids())if(raid.state().equals("OPEN")&&raid.ends()<=now&&!RansomStore.blocking(db,raid.id()))concludeSql(raid.id(),"EXPIRED","",now);
        var orphaned=new ArrayList<UUID>();try(var s=db.createStatement();var rows=s.executeQuery("SELECT DISTINCT c.raid FROM war_chunks c LEFT JOIN raids r ON r.id=c.raid WHERE c.state='CAPTURED' AND (r.id IS NULL OR r.state<>'OPEN')")){while(rows.next())orphaned.add(UUID.fromString(rows.getString(1)));}
        for(UUID raid:orphaned)WarCaptureStore.releaseWar(db,raid,"",now,"Восстановлено завершение старого рейда; временный контроль снят");
    }
    public CompletionStage<List<Raid>> expire(long now){if(now<0)throw new IllegalArgumentException("Неверное время");return submit(()->transaction(()->{expireSql(now);return readRaids();}));}
    /** Ransom and raid closure commit together on the same SQLite worker. */
    CompletionStage<List<Raid>> surrender(UUID raid,UUID actor,long now){return submit(()->transaction(()->{
        var ransom=RansomStore.read(db,raid);
        if(ransom==null||!ransom.state().equals("FUNDED"))throw new IllegalArgumentException("Выкуп ещё не дошёл до казны нападающих");
        concludeSql(raid,"SURRENDERED",actor.toString(),now);
        try(var p=db.prepareStatement("UPDATE raid_ransom SET state='DONE',note='Перевод подтверждён; война завершена' WHERE raid=? AND state='FUNDED'")){p.setString(1,raid.toString());if(p.executeUpdate()!=1)throw new SQLException("Квитанция выкупа изменилась");}
        return readRaids();
    }));}
    CompletionStage<List<Raid>> refundClosed(UUID raid,long now){return submit(()->transaction(()->{
        var ransom=RansomStore.read(db,raid);if(ransom==null||!ransom.state().equals("REFUNDED"))throw new IllegalArgumentException("Возврат ещё не подтверждён");
        concludeSql(raid,"CANCELLED","",now);
        try(var p=db.prepareStatement("UPDATE raid_ransom SET state='VOID',note='Выкуп возвращён защитникам; война нейтрально завершена' WHERE raid=? AND state='REFUNDED'")){p.setString(1,raid.toString());if(p.executeUpdate()!=1)throw new SQLException("Квитанция возврата изменилась");}
        return readRaids();
    }));}
    public CompletionStage<Map<String,UUID>> owners(){return submit(()->{var out=new HashMap<String,UUID>();try(var s=db.createStatement();var r=s.executeQuery("SELECT * FROM owners")){while(r.next())out.put(r.getString(1),UUID.fromString(r.getString(2)));}return Map.copyOf(out);});}
    public CompletionStage<Set<String>> damagedKeys(){return submit(()->{var out=new HashSet<String>();try(var s=db.createStatement();var r=s.executeQuery("SELECT key FROM hp UNION SELECT key FROM building_damage")){while(r.next())out.add(r.getString(1));}return Set.copyOf(out);});}
    public CompletionStage<Void> owner(Key key,UUID player){return submit(()->{
        try(var p=db.prepareStatement(player==null?"DELETE FROM owners WHERE key=?":"INSERT OR REPLACE INTO owners VALUES(?,?)")){p.setString(1,key.id());if(player!=null)p.setString(2,player.toString());p.executeUpdate();}return null;
    });}
    public CompletionStage<Void> reset(Key key){return repairMutation(()->{try(var p=db.prepareStatement("DELETE FROM hp WHERE key=?")){p.setString(1,key.id());p.executeUpdate();}BuildingDamageStore.reset(db,key.id());return null;});}
    public CompletionStage<List<Result>> damage(UUID blast,String actor,UUID attacker,UUID defender,long at,int repairSeconds,List<Hit> hits) {return repairMutation(()->transaction(()->{
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
            var registered=BuildingDamageStore.read(db,hit.key().id());
            if(!hit.project().isEmpty()&&registered!=null&&registered.town().equals(defender)&&registered.project().equals(hit.project())&&registered.material().equals(hit.material())){old=Math.min(hit.max(),registered.remaining());pending=registered.pending();}
            if(!pending.isEmpty())continue;
            double hp=Math.max(0,old-hit.damage());String receipt=hp==0?UUID.randomUUID().toString():"";
            try(var p=db.prepareStatement("INSERT OR REPLACE INTO hp VALUES(?,?,?,?,?,?,?,?)")) {
                p.setString(1,hit.key().id());p.setString(2,defender.toString());p.setString(3,hit.material());p.setLong(4,hit.generation());p.setDouble(5,hit.max());p.setDouble(6,hp);p.setLong(7,at);p.setString(8,receipt);p.executeUpdate();
            }
            BuildingDamageStore.hit(db,hit,hp,receipt,blast,at);
            results.add(new Result(hit,hp,receipt));
        }
        if(!results.isEmpty())audit(actor,attacker.toString(),defender.toString(),"TNT_HIT",at,results.size());
        try(var p=db.prepareStatement("DELETE FROM impacts WHERE at<?")){p.setLong(1,at-86400000L);p.executeUpdate();}
        return List.copyOf(results);
    }));}
    public CompletionStage<Void> finish(Result result,boolean destroyed){return repairMutation(()->{
        try(var p=db.prepareStatement(destroyed?"DELETE FROM hp WHERE key=? AND receipt=?":"UPDATE hp SET hp=1,receipt='' WHERE key=? AND receipt=?")) {
            p.setString(1,result.hit().key().id());p.setString(2,result.receipt());p.executeUpdate();
        }BuildingDamageStore.finish(db,result,destroyed);return null;
    });}
    public CompletionStage<Health> health(Key key,String material,double max,long now,int repair){return submit(()->{
        var registered=BuildingDamageStore.read(db,key.id());if(registered!=null&&registered.material().equals(material))return new Health(Math.min(max,registered.remaining()),max,registered.at());
        try(var p=db.prepareStatement("SELECT * FROM hp WHERE key=?")){p.setString(1,key.id());try(var r=p.executeQuery()){if(r.next()&&r.getString("material").equals(material)&&now-r.getLong("last_hit")<repair*1000L){double hp=r.getDouble("hp");if(!Double.isFinite(hp)||hp<0||hp>1_000_000)throw new SQLException("Повреждена прочность блока");return new Health(Math.min(max,hp),max,r.getLong("last_hit"));}}}
        return new Health(max,max,0);
    });}
    void audit(String actor,String attacker,String defender,String action,long now,int blocks)throws SQLException {
        try(var p=db.prepareStatement("INSERT INTO audit(actor,attacker,defender,action,at,blocks) VALUES(?,?,?,?,?,?)")){p.setString(1,actor);p.setString(2,attacker);p.setString(3,defender);p.setString(4,action);p.setLong(5,now);p.setInt(6,blocks);p.executeUpdate();}
    }
    public CompletionStage<List<Audit>> history(UUID town){return submit(()->{var out=new ArrayList<Audit>();try(var p=db.prepareStatement("SELECT * FROM audit WHERE attacker=? OR defender=? ORDER BY id DESC LIMIT 10")){p.setString(1,town.toString());p.setString(2,town.toString());try(var r=p.executeQuery()){while(r.next())out.add(new Audit(r.getString("actor"),r.getString("attacker"),r.getString("defender"),r.getString("action"),r.getLong("at"),r.getInt("blocks")));}}return List.copyOf(out);});}
    @Override public void close(){closed=true;healthy=false;worker.shutdown(); /* queued commits finish; connection closes on the worker */}
}
