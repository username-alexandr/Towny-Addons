package ru.neverland.mintcontracts.orders;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** One bounded database worker. Never waits for Bukkit or acquires the suite ledger. */
public final class ServerMaterials implements AutoCloseable {
    public record Delivery(UUID id,UUID player,String playerName,String template,String material,int amount,long cents,String rarity,long created,String state,boolean settled) {
        public Delivery {
            Objects.requireNonNull(id);Objects.requireNonNull(player);if(playerName==null||playerName.isBlank()||playerName.length()>64)throw new IllegalArgumentException("Неверное имя игрока");
            if(!template.matches("[a-z0-9_]{1,48}")||!material.matches("[A-Z0-9_]{1,64}")||amount<1||amount>100000||cents<1||created<0
                    ||!Set.of("COMMON","UNCOMMON","RARE","EPIC","LEGENDARY").contains(rarity)||!Set.of("PREPARED","CONFIRMED","VOID").contains(state))throw new IllegalArgumentException("Неверная поставка");
        }
        public Delivery(UUID id,UUID player,String template,String material,int amount,long cents,String rarity,long created){this(id,player,player.toString(),template,material,amount,cents,rarity,created,"PREPARED",false);}
        public Delivery(UUID id,UUID player,String name,String template,String material,int amount,long cents,String rarity,long created){this(id,player,name,template,material,amount,cents,rarity,created,"PREPARED",false);}
        boolean same(Delivery d){return id.equals(d.id)&&player.equals(d.player)&&template.equals(d.template)&&material.equals(d.material)&&amount==d.amount&&cents==d.cents&&rarity.equals(d.rarity);}
    }
    public record Balance(String material,long received,long available) {}
    public record Allocation(UUID id,String project,String actor,String reason,String state,Map<String,Long> items) {public Allocation{items=Map.copyOf(items);}}
    public record Project(String id,String title,String actor,String state,Map<String,Long> needs){public Project{needs=Map.copyOf(needs);}}
    @FunctionalInterface private interface Sql<T>{T run()throws Exception;}
    private final Path file;
    private final ThreadPoolExecutor worker;
    private Connection connection;
    private volatile boolean fault,closing;
    private final CompletableFuture<Void> closed=new CompletableFuture<>();
    private final CompletableFuture<Void> ready;
    public ServerMaterials(Path file){
        this.file=file.toAbsolutePath();
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),r->{Thread t=new Thread(r,"NeverLand-order-materials");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){
            @Override protected void terminated(){try{if(connection!=null)connection.close();closed.complete(null);}catch(SQLException error){closed.completeExceptionally(error);}}
        };
        ready=submit(()->{try{open();return null;}catch(Exception error){fault=true;throw error;}});
    }
    public CompletionStage<Void> ready(){return ready;}
    public CompletionStage<Void> closed(){return closed;}
    public boolean available(){return ready.isDone()&&!ready.isCompletedExceptionally()&&!fault&&!closing;}
    private <T> CompletableFuture<T> submit(Sql<T> task){
        var result=new CompletableFuture<T>();
        if(closing){result.completeExceptionally(new IOException("Серверный склад останавливается"));return result;}
        try{worker.execute(()->{try{if(fault)throw new IOException("База склада остановлена после ошибки; нужна проверка");result.complete(task.run());}
            catch(Throwable error){if(error instanceof SQLException||error instanceof IOException)fault=true;result.completeExceptionally(error);}});}
        catch(RejectedExecutionException error){result.completeExceptionally(new IOException("Склад занят; повторите действие позже",error));}
        return result;
    }
    private void open()throws Exception{
        Files.createDirectories(file.getParent());Class.forName("org.sqlite.JDBC");
        connection=DriverManager.getConnection("jdbc:sqlite:"+file);
        try(var s=connection.createStatement()){
            s.execute("PRAGMA busy_timeout=5000");s.execute("PRAGMA foreign_keys=ON");
            try(var r=s.executeQuery("PRAGMA journal_mode=WAL")){if(!r.next()||!r.getString(1).equalsIgnoreCase("wal"))throw new IOException("Склад требует файловую систему с поддержкой SQLite WAL");}
            s.execute("PRAGMA synchronous=FULL");
            try(var r=s.executeQuery("PRAGMA user_version")){if(!r.next()||r.getInt(1)>2)throw new IOException("Неизвестная версия базы склада");}
            try(var r=s.executeQuery("PRAGMA quick_check")){if(!r.next()||!r.getString(1).equals("ok"))throw new IOException("База склада повреждена");}
            s.execute("CREATE TABLE IF NOT EXISTS deliveries (id TEXT PRIMARY KEY,player TEXT NOT NULL,player_name TEXT NOT NULL,template TEXT NOT NULL,material TEXT NOT NULL,amount INTEGER NOT NULL CHECK(amount>0),cents INTEGER NOT NULL CHECK(cents>0),rarity TEXT NOT NULL,created INTEGER NOT NULL,state TEXT NOT NULL CHECK(state IN ('PREPARED','CONFIRMED','VOID')),settled INTEGER NOT NULL DEFAULT 0,confirmed INTEGER NOT NULL DEFAULT 0,decision TEXT NOT NULL DEFAULT '')");
            s.execute("CREATE TABLE IF NOT EXISTS balances (material TEXT PRIMARY KEY,received INTEGER NOT NULL CHECK(received>=0),available INTEGER NOT NULL CHECK(available>=0 AND available<=received))");
            s.execute("CREATE TABLE IF NOT EXISTS allocations (id TEXT PRIMARY KEY,project TEXT NOT NULL,actor TEXT NOT NULL,reason TEXT NOT NULL,state TEXT NOT NULL CHECK(state IN ('RESERVED','SPENT','RETURNED')),created INTEGER NOT NULL,decision TEXT NOT NULL DEFAULT '')");
            s.execute("CREATE TABLE IF NOT EXISTS allocation_items (id TEXT NOT NULL REFERENCES allocations(id),material TEXT NOT NULL,amount INTEGER NOT NULL CHECK(amount>0),PRIMARY KEY(id,material))");
            s.execute("CREATE TABLE IF NOT EXISTS projects (id TEXT PRIMARY KEY,title TEXT NOT NULL,actor TEXT NOT NULL,state TEXT NOT NULL CHECK(state IN ('OPEN','CANCELLED','ALLOCATED')),created INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS project_needs (project TEXT NOT NULL REFERENCES projects(id),material TEXT NOT NULL,amount INTEGER NOT NULL CHECK(amount>0),PRIMARY KEY(project,material))");
            s.execute("CREATE TABLE IF NOT EXISTS project_actions (project TEXT NOT NULL REFERENCES projects(id),action TEXT NOT NULL,actor TEXT NOT NULL,reason TEXT NOT NULL,at INTEGER NOT NULL,PRIMARY KEY(project,action))");
            s.execute("CREATE INDEX IF NOT EXISTS deliveries_pending ON deliveries(settled,state)");s.execute("PRAGMA user_version=2");
        }
    }
    private <T> T transaction(Sql<T> task)throws Exception{
        connection.setAutoCommit(false);
        try{T value=task.run();connection.commit();return value;}catch(Throwable error){try{connection.rollback();}catch(SQLException suppressed){error.addSuppressed(suppressed);}throw error;}
        finally{connection.setAutoCommit(true);}
    }
    private Delivery row(ResultSet r)throws SQLException{return new Delivery(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("player")),r.getString("player_name"),r.getString("template"),r.getString("material"),r.getInt("amount"),r.getLong("cents"),r.getString("rarity"),r.getLong("created"),r.getString("state"),r.getInt("settled")==1);}
    private Delivery get(UUID id)throws SQLException{try(var s=connection.prepareStatement("SELECT * FROM deliveries WHERE id=?")){s.setString(1,id.toString());try(var r=s.executeQuery()){return r.next()?row(r):null;}}}
    public CompletionStage<Delivery> delivery(UUID id){return submit(()->get(id));}
    public CompletionStage<Delivery> prepare(Delivery delivery){return submit(()->transaction(()->{
        Delivery old=get(delivery.id());if(old!=null){if(!old.same(delivery))throw new IllegalArgumentException("ID поставки уже связан с другими ресурсами");if(old.state().equals("VOID"))throw new IllegalArgumentException("Поставка отменена при сверке");return old;}
        try(var s=connection.prepareStatement("INSERT INTO deliveries(id,player,player_name,template,material,amount,cents,rarity,created,state) VALUES(?,?,?,?,?,?,?,?,?, 'PREPARED')")){
            s.setString(1,delivery.id().toString());s.setString(2,delivery.player().toString());s.setString(3,delivery.playerName());s.setString(4,delivery.template());s.setString(5,delivery.material());s.setInt(6,delivery.amount());s.setLong(7,delivery.cents());s.setString(8,delivery.rarity());s.setLong(9,delivery.created());s.executeUpdate();
        }return get(delivery.id());
    }));}
    public CompletionStage<Delivery> confirm(UUID id,String proof){return decide(id,true,proof);}
    public CompletionStage<Delivery> voidDelivery(UUID id,String proof){return decide(id,false,proof);}
    private CompletionStage<Delivery> decide(UUID id,boolean taken,String proof){
        if(proof==null||proof.trim().length()<8)return CompletableFuture.failedFuture(new IllegalArgumentException("Нужно основание сверки"));
        return submit(()->transaction(()->{
            Delivery d=get(id);if(d==null)throw new IllegalArgumentException("Поставка не найдена");String next=taken?"CONFIRMED":"VOID";
            if(d.state().equals(next))return d;if(!d.state().equals("PREPARED"))throw new IllegalArgumentException("Подтверждённую поставку нельзя изменить");
            if(taken){
                long received=0,available=0;try(var s=connection.prepareStatement("SELECT received,available FROM balances WHERE material=?")){s.setString(1,d.material());try(var r=s.executeQuery()){if(r.next()){received=r.getLong(1);available=r.getLong(2);}}}
                try(var s=connection.prepareStatement("INSERT INTO balances(material,received,available) VALUES(?,?,?) ON CONFLICT(material) DO UPDATE SET received=excluded.received,available=excluded.available")){
                    s.setString(1,d.material());s.setLong(2,Math.addExact(received,d.amount()));s.setLong(3,Math.addExact(available,d.amount()));s.executeUpdate();
                }
            }
            try(var s=connection.prepareStatement("UPDATE deliveries SET state=?,confirmed=?,decision=? WHERE id=?")){s.setString(1,next);s.setLong(2,System.currentTimeMillis());s.setString(3,proof);s.setString(4,id.toString());s.executeUpdate();}
            return get(id);
        }));
    }
    public CompletionStage<Void> settled(UUID id){return submit(()->{try(var s=connection.prepareStatement("UPDATE deliveries SET settled=1 WHERE id=? AND state!='PREPARED'")){s.setString(1,id.toString());s.executeUpdate();}return null;});}
    public CompletionStage<List<Delivery>> unsettled(){return submit(()->{var result=new ArrayList<Delivery>();try(var s=connection.createStatement();var r=s.executeQuery("SELECT * FROM deliveries WHERE settled=0 AND state!='PREPARED' ORDER BY created LIMIT 20")){while(r.next())result.add(row(r));}return List.copyOf(result);});}
    public CompletionStage<List<Balance>> balances(){return submit(()->{var result=new ArrayList<Balance>();try(var s=connection.createStatement();var r=s.executeQuery("SELECT material,received,available FROM balances ORDER BY material")){while(r.next())result.add(new Balance(r.getString(1),r.getLong(2),r.getLong(3)));}return List.copyOf(result);});}
    private Project project(String id)throws SQLException{
        try(var s=connection.prepareStatement("SELECT * FROM projects WHERE id=?")){s.setString(1,id);try(var r=s.executeQuery()){
            if(!r.next())return null;var needs=new TreeMap<String,Long>();try(var q=connection.prepareStatement("SELECT material,amount FROM project_needs WHERE project=?")){q.setString(1,id);try(var n=q.executeQuery()){while(n.next())needs.put(n.getString(1),n.getLong(2));}}
            return new Project(id,r.getString("title"),r.getString("actor"),r.getString("state"),needs);
        }}
    }
    private void projectLog(String project,String action,String actor,String reason)throws SQLException{
        try(var s=connection.prepareStatement("INSERT OR IGNORE INTO project_actions VALUES(?,?,?,?,?)")){s.setString(1,project);s.setString(2,action);s.setString(3,actor);s.setString(4,reason);s.setLong(5,System.currentTimeMillis());s.executeUpdate();}
    }
    public CompletionStage<List<String>> projectHistory(String id){return submit(()->{var out=new ArrayList<String>();try(var s=connection.prepareStatement("SELECT action,actor,reason,at FROM project_actions WHERE project=? ORDER BY at,action")){s.setString(1,id);try(var r=s.executeQuery()){while(r.next())out.add(r.getString(1)+" | "+r.getString(2)+" | "+r.getString(3)+" | "+r.getLong(4));}}return List.copyOf(out);});}
    public CompletionStage<Project> plan(String id,String title,String actor,Map<String,Long> costs){
        var needs=Map.copyOf(costs);
        if(id==null||!id.matches("[a-z0-9_]{1,48}")||title==null||title.isBlank()||title.length()>128||actor==null||actor.isBlank()||actor.length()>64||needs.isEmpty()||needs.size()>256||needs.entrySet().stream().anyMatch(e->!e.getKey().matches("[A-Z0-9_]{1,64}")||e.getValue()<1||e.getValue()>1000000000L))return CompletableFuture.failedFuture(new IllegalArgumentException("Неверный проект или список материалов"));
        return submit(()->transaction(()->{
            var old=project(id);if(old!=null){if(!old.title().equals(title)||!old.actor().equals(actor)||!old.needs().equals(needs))throw new IllegalArgumentException("ID проекта уже занят. Для другой сметы нужен новый ID");return old;}
            try(var q=connection.createStatement();var r=q.executeQuery("SELECT count(*) FROM projects WHERE state='OPEN'")){if(r.next()&&r.getInt(1)>=1000)throw new IllegalArgumentException("Сначала завершите один из открытых серверных проектов");}
            try(var s=connection.prepareStatement("INSERT INTO projects VALUES(?,?,?,'OPEN',?)")){s.setString(1,id);s.setString(2,title);s.setString(3,actor);s.setLong(4,System.currentTimeMillis());s.executeUpdate();}
            for(var entry:needs.entrySet())try(var s=connection.prepareStatement("INSERT INTO project_needs VALUES(?,?,?)")){s.setString(1,id);s.setString(2,entry.getKey());s.setLong(3,entry.getValue());s.executeUpdate();}
            projectLog(id,"PLAN",actor,title);
            return project(id);
        }));
    }
    public CompletionStage<Map<String,Long>> demand(){return submit(()->{
        var out=new TreeMap<String,Long>();try(var s=connection.createStatement();var r=s.executeQuery("SELECT n.material,SUM(n.amount)-COALESCE(b.available,0) AS missing FROM project_needs n JOIN projects p ON p.id=n.project LEFT JOIN balances b ON b.material=n.material WHERE p.state='OPEN' GROUP BY n.material HAVING missing>0")){while(r.next())out.put(r.getString(1),r.getLong(2));}return Map.copyOf(out);
    });}
    public CompletionStage<List<Project>> projects(int page){
        if(page<1||page>100000)return CompletableFuture.failedFuture(new IllegalArgumentException("Неверная страница"));
        return submit(()->{var ids=new ArrayList<String>();try(var s=connection.prepareStatement("SELECT id FROM projects ORDER BY created DESC,id LIMIT 20 OFFSET ?")){s.setLong(1,(page-1L)*20);try(var r=s.executeQuery()){while(r.next())ids.add(r.getString(1));}}var out=new ArrayList<Project>();for(String id:ids)out.add(project(id));return List.copyOf(out);});
    }
    public CompletionStage<Project> cancelProject(String id,String actor,String reason){
        if(actor==null||actor.isBlank()||reason==null||reason.trim().length()<8)return CompletableFuture.failedFuture(new IllegalArgumentException("Нужны автор и основание отмены"));
        return submit(()->transaction(()->{
        var row=project(id);if(row==null)throw new IllegalArgumentException("Проект не найден");if(row.state().equals("ALLOCATED"))throw new IllegalArgumentException("Материалы уже в резерве. Завершите или верните резерв по его ID");
        try(var s=connection.prepareStatement("UPDATE projects SET state='CANCELLED' WHERE id=? AND state='OPEN'")){s.setString(1,id);s.executeUpdate();}projectLog(id,"CANCEL",actor,reason);return project(id);
    }));}
    public static UUID projectReservation(String id){return UUID.nameUUIDFromBytes(("neverland-server-project:"+id).getBytes(StandardCharsets.UTF_8));}
    public CompletionStage<Allocation> reserveProject(String id,String actor,String reason){
        if(actor==null||actor.isBlank()||reason==null||reason.trim().length()<8)return CompletableFuture.failedFuture(new IllegalArgumentException("Нужны автор и основание резерва"));
        return submit(()->transaction(()->{
            var row=project(id);if(row==null||row.state().equals("CANCELLED"))throw new IllegalArgumentException("Открытый проект не найден");UUID receipt=projectReservation(id);
            if(row.state().equals("ALLOCATED")){var old=allocation(receipt);if(old==null||!old.project().equals(id)||!old.items().equals(row.needs()))throw new IOException("Резерв проекта требует сверки");return old;}
            var allocation=allocateNow(receipt,id,actor,reason,row.needs());
            try(var s=connection.prepareStatement("UPDATE projects SET state='ALLOCATED' WHERE id=? AND state='OPEN'")){s.setString(1,id);s.executeUpdate();}projectLog(id,"RESERVE",actor,reason);return allocation;
        }));
    }
    public CompletionStage<List<Delivery>> history(int page){if(page<1||page>100000)return CompletableFuture.failedFuture(new IllegalArgumentException("Неверная страница"));return submit(()->{var result=new ArrayList<Delivery>();try(var s=connection.prepareStatement("SELECT * FROM deliveries ORDER BY created DESC,id LIMIT 20 OFFSET ?")){s.setLong(1,(page-1L)*20);try(var r=s.executeQuery()){while(r.next())result.add(row(r));}}return List.copyOf(result);});}
    private Allocation allocation(UUID id)throws SQLException{
        try(var s=connection.prepareStatement("SELECT * FROM allocations WHERE id=?")){s.setString(1,id.toString());try(var r=s.executeQuery()){
            if(!r.next())return null;var items=new TreeMap<String,Long>();try(var q=connection.prepareStatement("SELECT material,amount FROM allocation_items WHERE id=?")){q.setString(1,id.toString());try(var rs=q.executeQuery()){while(rs.next())items.put(rs.getString(1),rs.getLong(2));}}
            return new Allocation(id,r.getString("project"),r.getString("actor"),r.getString("reason"),r.getString("state"),items);
        }}
    }
    public CompletionStage<Allocation> allocate(UUID id,String project,String actor,String reason,Map<String,Long> costs){
        Map<String,Long> items=Map.copyOf(costs);
        if(id==null||project==null||!project.matches("[a-zA-Z0-9_-]{1,48}")||actor==null||actor.isBlank()||reason==null||reason.trim().length()<8||items.isEmpty()||items.size()>256||items.entrySet().stream().anyMatch(e->!e.getKey().matches("[A-Z0-9_]{1,64}")||e.getValue()<1))return CompletableFuture.failedFuture(new IllegalArgumentException("Неверный резерв стройки"));
        return submit(()->transaction(()->allocateNow(id,project,actor,reason,items)));
    }
    private Allocation allocateNow(UUID id,String project,String actor,String reason,Map<String,Long> items)throws Exception{
            Allocation old=allocation(id);if(old!=null){if(!old.project().equals(project)||!old.actor().equals(actor)||!old.reason().equals(reason)||!old.items().equals(items))throw new IllegalArgumentException("ID резерва уже использован");return old;}
            try(var s=connection.prepareStatement("INSERT INTO allocations(id,project,actor,reason,state,created) VALUES(?,?,?,?,'RESERVED',?)")){s.setString(1,id.toString());s.setString(2,project);s.setString(3,actor);s.setString(4,reason);s.setLong(5,System.currentTimeMillis());s.executeUpdate();}
            for(var e:items.entrySet()){
                try(var s=connection.prepareStatement("UPDATE balances SET available=available-? WHERE material=? AND available>=?")){s.setLong(1,e.getValue());s.setString(2,e.getKey());s.setLong(3,e.getValue());if(s.executeUpdate()!=1)throw new IllegalArgumentException("На серверном складе недостаточно "+e.getKey());}
                try(var s=connection.prepareStatement("INSERT INTO allocation_items(id,material,amount) VALUES(?,?,?)")){s.setString(1,id.toString());s.setString(2,e.getKey());s.setLong(3,e.getValue());s.executeUpdate();}
            }return allocation(id);
    }
    public CompletionStage<Allocation> allocation(UUID id,String decision,String proof){
        if(!Set.of("consume","return").contains(decision)||proof==null||proof.trim().length()<8)return CompletableFuture.failedFuture(new IllegalArgumentException("consume | return и основание"));
        return submit(()->transaction(()->{
            Allocation old=allocation(id);if(old==null)throw new IllegalArgumentException("Резерв не найден");String next=decision.equals("consume")?"SPENT":"RETURNED";
            if(old.state().equals(next))return old;if(!old.state().equals("RESERVED"))throw new IllegalArgumentException("Завершённый резерв нельзя изменить");
            if(next.equals("RETURNED"))for(var e:old.items().entrySet())try(var s=connection.prepareStatement("UPDATE balances SET available=available+? WHERE material=?")){s.setLong(1,e.getValue());s.setString(2,e.getKey());s.executeUpdate();}
            try(var s=connection.prepareStatement("UPDATE allocations SET state=?,decision=? WHERE id=?")){s.setString(1,next);s.setString(2,proof);s.setString(3,id.toString());s.executeUpdate();}return allocation(id);
        }));
    }
    public CompletionStage<List<Allocation>> allocations(int page){if(page<1||page>100000)return CompletableFuture.failedFuture(new IllegalArgumentException("Неверная страница"));return submit(()->{var ids=new ArrayList<UUID>();try(var s=connection.prepareStatement("SELECT id FROM allocations ORDER BY created DESC,id LIMIT 20 OFFSET ?")){s.setLong(1,(page-1L)*20);try(var r=s.executeQuery()){while(r.next())ids.add(UUID.fromString(r.getString(1)));}}var result=new ArrayList<Allocation>();for(UUID id:ids)result.add(allocation(id));return List.copyOf(result);});}
    public CompletionStage<Path> export(){return submit(()->{
        Path folder=file.getParent().resolve("resource-exports");Files.createDirectories(folder);Path target=folder.resolve("deliveries-"+UUID.randomUUID()+".csv");
        try(var writer=Files.newBufferedWriter(target,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);var s=connection.createStatement();var r=s.executeQuery("SELECT * FROM deliveries ORDER BY created,id")){
            writer.write("order_id,player_uuid,player_name,template,material,amount,rarity,reward_cents,created,state,confirmed,decision\n");
            while(r.next()){var line=new ArrayList<String>();for(String key:List.of("id","player","player_name","template","material","amount","rarity","cents","created","state","confirmed","decision")){String value=r.getString(key);if(value.startsWith("=")||value.startsWith("+")||value.startsWith("-")||value.startsWith("@"))value="'"+value;line.add("\""+value.replace("\"","\"\"")+"\"");}writer.write(String.join(",",line)+"\n");}
        }return target;
    });}
    @Override public synchronized void close(){if(closing)return;closing=true;worker.shutdown();}
}
