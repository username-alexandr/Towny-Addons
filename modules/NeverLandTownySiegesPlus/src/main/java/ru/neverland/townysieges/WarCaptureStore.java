package ru.neverland.townysieges;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
/** Additive migration. Only the raid storage worker touches this table. */
final class WarCaptureStore {
    record Key(UUID raid,UUID world,int x,int z){}
    record Capture(Key key,UUID attacker,UUID defender,UUID actor,String method,long at,String state){}
    record Log(long id,Key key,UUID attacker,UUID defender,UUID actor,String action,long at,String detail){}
    private final RaidStore store;
    WarCaptureStore(RaidStore store){this.store=store;}
    static void initialize(Connection db)throws SQLException{try(var s=db.createStatement()){
        s.execute("CREATE TABLE IF NOT EXISTS war_chunks(raid TEXT NOT NULL,world TEXT NOT NULL,x INTEGER NOT NULL,z INTEGER NOT NULL,attacker TEXT NOT NULL,defender TEXT NOT NULL,actor TEXT NOT NULL,method TEXT NOT NULL,at INTEGER NOT NULL,state TEXT NOT NULL,released INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(raid,world,x,z))");
        s.execute("CREATE INDEX IF NOT EXISTS war_chunks_state ON war_chunks(state)");
        s.execute("CREATE TABLE IF NOT EXISTS war_chunk_log(id INTEGER PRIMARY KEY AUTOINCREMENT,raid TEXT NOT NULL,world TEXT NOT NULL,x INTEGER NOT NULL,z INTEGER NOT NULL,attacker TEXT NOT NULL,defender TEXT NOT NULL,actor TEXT NOT NULL,action TEXT NOT NULL,at INTEGER NOT NULL,detail TEXT NOT NULL)");
        boolean journalled=false;try(var r=s.executeQuery("PRAGMA table_info(war_chunk_log)")){while(r.next())if(r.getString("name").equals("journalled"))journalled=true;}
        if(!journalled)s.execute("ALTER TABLE war_chunk_log ADD COLUMN journalled INTEGER NOT NULL DEFAULT 0");
        s.execute("CREATE INDEX IF NOT EXISTS war_chunk_pending ON war_chunk_log(journalled,id)");
    }}
    private static Capture read(ResultSet r)throws SQLException{return new Capture(new Key(UUID.fromString(r.getString("raid")),UUID.fromString(r.getString("world")),r.getInt("x"),r.getInt("z")),UUID.fromString(r.getString("attacker")),UUID.fromString(r.getString("defender")),UUID.fromString(r.getString("actor")),r.getString("method"),r.getLong("at"),r.getString("state"));}
    CompletionStage<List<Capture>> load(){return store.submit(()->{var out=new ArrayList<Capture>();try(var s=store.connection().createStatement();var r=s.executeQuery("SELECT * FROM war_chunks WHERE state='CAPTURED'")){while(r.next())out.add(read(r));}return List.copyOf(out);});}
    CompletionStage<Boolean> capture(Capture c){return store.submit(()->store.transaction(()->{
        try(var p=store.connection().prepareStatement("SELECT 1 FROM raids WHERE id=? AND attacker=? AND defender=? AND state='OPEN' AND starts<=? AND ends>?")){
            p.setString(1,c.key().raid().toString());p.setString(2,c.attacker().toString());p.setString(3,c.defender().toString());p.setLong(4,System.currentTimeMillis());p.setLong(5,System.currentTimeMillis());try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Война уже не идёт");}
        }
        try(var p=store.connection().prepareStatement("INSERT OR IGNORE INTO war_chunks(raid,world,x,z,attacker,defender,actor,method,at,state) VALUES(?,?,?,?,?,?,?,?,?,'CAPTURED')")){
            bind(p,c.key());p.setString(5,c.attacker().toString());p.setString(6,c.defender().toString());p.setString(7,c.actor().toString());p.setString(8,c.method());p.setLong(9,c.at());if(p.executeUpdate()==0)return false;
        }
        log(c.key(),c.attacker(),c.defender(),c.actor(),"CAPTURED",c.at(),c.method().equals("BUILDING")?"Разрушены части всех городских зданий в чанке":"Пустой участок удержан");return true;
    }));}
    CompletionStage<Boolean> release(Capture c,long now){return store.submit(()->store.transaction(()->{try(var p=store.connection().prepareStatement("UPDATE war_chunks SET state='RELEASED',released=? WHERE raid=? AND world=? AND x=? AND z=? AND state='CAPTURED'")){
        p.setLong(1,now);p.setString(2,c.key().raid().toString());p.setString(3,c.key().world().toString());p.setInt(4,c.key().x());p.setInt(5,c.key().z());if(p.executeUpdate()==0)return false;
    }log(c.key(),c.attacker(),c.defender(),c.actor(),"RELEASED",now,"Война завершена, остановлена или потерян лагерь");return true;}));}
    CompletionStage<Void> event(Key k,UUID attacker,UUID defender,UUID actor,String action,String detail){long now=System.currentTimeMillis();return store.submit(()->{log(k,attacker,defender,actor,action,now,detail);return null;});}
    CompletionStage<List<Log>> pendingLog(){return store.submit(()->{var out=new ArrayList<Log>();try(var s=store.connection().createStatement();var r=s.executeQuery("SELECT * FROM war_chunk_log WHERE journalled=0 ORDER BY id LIMIT 32")){
        while(r.next()){String actor=r.getString("actor");out.add(new Log(r.getLong("id"),new Key(UUID.fromString(r.getString("raid")),UUID.fromString(r.getString("world")),r.getInt("x"),r.getInt("z")),UUID.fromString(r.getString("attacker")),UUID.fromString(r.getString("defender")),actor.isEmpty()?null:UUID.fromString(actor),r.getString("action"),r.getLong("at"),r.getString("detail")));}
    }return List.copyOf(out);});}
    CompletionStage<Void> journalled(Collection<Long> ids){return store.submit(()->{try(var p=store.connection().prepareStatement("UPDATE war_chunk_log SET journalled=1 WHERE id=?")){for(long id:ids){p.setLong(1,id);p.addBatch();}p.executeBatch();}return null;});}
    private void log(Key k,UUID attacker,UUID defender,UUID actor,String action,long now,String detail)throws SQLException{try(var p=store.connection().prepareStatement("INSERT INTO war_chunk_log(raid,world,x,z,attacker,defender,actor,action,at,detail) VALUES(?,?,?,?,?,?,?,?,?,?)")){
        bind(p,k);p.setString(5,attacker.toString());p.setString(6,defender.toString());p.setString(7,actor==null?"":actor.toString());p.setString(8,action);p.setLong(9,now);p.setString(10,detail);p.executeUpdate();
    }}
    private static void bind(PreparedStatement p,Key k)throws SQLException{p.setString(1,k.raid().toString());p.setString(2,k.world().toString());p.setInt(3,k.x());p.setInt(4,k.z());}
}
