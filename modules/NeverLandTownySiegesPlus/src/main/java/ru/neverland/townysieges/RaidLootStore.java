package ru.neverland.townysieges;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Durable saga: an interrupted external money call always requires reconciliation. */
public final class RaidLootStore {
    public record Loot(UUID id,UUID raid,UUID attacker,UUID defender,UUID actor,RaidStore.Key core,byte[] book,
            int moneyBps,int resourcesBps,long cents,long debitBefore,long creditBefore,long created,String state,String note) {
        public Loot {book=book.clone();if(cents<0||debitBefore<0||creditBefore<0||moneyBps<0||moneyBps>10000||resourcesBps<0||resourcesBps>10000)throw new IllegalArgumentException("Повреждена квитанция добычи");}
        @Override public byte[] book(){return book.clone();}
    }
    private final RaidStore storage;
    public RaidLootStore(RaidStore storage){this.storage=storage;}
    static void initialize(Connection db)throws SQLException {
        try(var s=db.createStatement()){
            s.execute("CREATE TABLE IF NOT EXISTS raid_loot(id TEXT PRIMARY KEY,raid TEXT NOT NULL,attacker TEXT NOT NULL,defender TEXT NOT NULL,actor TEXT NOT NULL,world TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,book BLOB NOT NULL,money_bps INTEGER NOT NULL,resources_bps INTEGER NOT NULL,cents INTEGER NOT NULL,debit_before INTEGER NOT NULL,credit_before INTEGER NOT NULL,created INTEGER NOT NULL,state TEXT NOT NULL,note TEXT NOT NULL,UNIQUE(raid,defender))");
            s.execute("UPDATE raid_loot SET state='REVIEW_CORE',note='restart: core destruction outcome unknown' WHERE state='ARMED'");
            s.execute("UPDATE raid_loot SET state='REVIEW_DEBIT',note='restart: withdrawal outcome unknown' WHERE state='DEBITING'");
            s.execute("UPDATE raid_loot SET state='REVIEW_CREDIT',note='restart: deposit outcome unknown' WHERE state='CREDITING'");
        }
    }
    private Connection db(){return storage.connection();}
    public static UUID operation(UUID raid,UUID defender){return UUID.nameUUIDFromBytes(("raid-loot:"+raid+":"+defender).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    public CompletionStage<Loot> arm(RaidController.Permit permit,RaidStore.Key core,byte[] book,LootSettings settings){byte[] saved=book.clone();return storage.submit(()->{
        UUID id=operation(permit.raid(),permit.defender());var old=read(id);if(old!=null&&!old.state().equals("VOID"))return old;
        try(var p=db().prepareStatement("INSERT OR REPLACE INTO raid_loot VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){
            p.setString(1,id.toString());p.setString(2,permit.raid().toString());p.setString(3,permit.attacker().toString());p.setString(4,permit.defender().toString());p.setString(5,permit.actor().toString());p.setString(6,core.world().toString());p.setInt(7,core.x());p.setInt(8,core.y());p.setInt(9,core.z());p.setBytes(10,saved);p.setInt(11,settings.moneyBps());p.setInt(12,settings.resourcesBps());p.setLong(13,0);p.setLong(14,0);p.setLong(15,0);p.setLong(16,System.currentTimeMillis());p.setString(17,"ARMED");p.setString(18,"");p.executeUpdate();
        }return read(id);
    });}
    private Loot row(ResultSet r)throws SQLException{return new Loot(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("raid")),UUID.fromString(r.getString("attacker")),UUID.fromString(r.getString("defender")),UUID.fromString(r.getString("actor")),new RaidStore.Key(UUID.fromString(r.getString("world")),r.getInt("x"),r.getInt("y"),r.getInt("z")),r.getBytes("book"),r.getInt("money_bps"),r.getInt("resources_bps"),r.getLong("cents"),r.getLong("debit_before"),r.getLong("credit_before"),r.getLong("created"),r.getString("state"),r.getString("note"));}
    private Loot read(UUID id)throws SQLException{try(var p=db().prepareStatement("SELECT * FROM raid_loot WHERE id=?")){p.setString(1,id.toString());try(var r=p.executeQuery()){return r.next()?row(r):null;}}}
    public CompletionStage<Loot> get(UUID id){return storage.submit(()->read(id));}
    public CompletionStage<List<Loot>> list(boolean pending){return storage.submit(()->{var out=new ArrayList<Loot>();try(var s=db().createStatement();var r=s.executeQuery("SELECT * FROM raid_loot "+(pending?"WHERE state NOT IN ('DONE','VOID') ORDER BY CASE WHEN state LIKE 'REVIEW_%' THEN 1 ELSE 0 END,created ASC LIMIT 100":"ORDER BY created DESC LIMIT 100"))){while(r.next())out.add(row(r));}return List.copyOf(out);});}
    public CompletionStage<Loot> change(UUID id,String expected,String next,String note){return storage.submit(()->{
        try(var p=db().prepareStatement("UPDATE raid_loot SET state=?,note=? WHERE id=? AND state=?")){p.setString(1,next);p.setString(2,note);p.setString(3,id.toString());p.setString(4,expected);if(p.executeUpdate()!=1)throw new IllegalArgumentException("Состояние добычи изменилось");}
        var result=read(id);storage.audit(result.actor().toString(),result.attacker().toString(),result.defender().toString(),"LOOT_"+next,System.currentTimeMillis(),0);return result;
    });}
    public CompletionStage<Loot> prepare(UUID id,long cents,long debitBefore,long creditBefore){return storage.submit(()->{
        if(cents<0||cents>debitBefore||creditBefore<0)throw new IllegalArgumentException("Неверная сумма добычи");
        try(var p=db().prepareStatement("UPDATE raid_loot SET state='PREPARED',cents=?,debit_before=?,credit_before=?,note='' WHERE id=? AND state='BREACHED'")){p.setLong(1,cents);p.setLong(2,debitBefore);p.setLong(3,creditBefore);p.setString(4,id.toString());if(p.executeUpdate()!=1)throw new IllegalArgumentException("Добыча уже подготовлена");}return read(id);
    });}
    public CompletionStage<Loot> reprice(UUID id,long cents,long available){return storage.submit(()->{var old=read(id);if(old==null||!old.state().equals("PREPARED")||cents<0||cents>old.cents()||cents>available)throw new IllegalArgumentException("Нельзя увеличить добычу");try(var p=db().prepareStatement("UPDATE raid_loot SET cents=?,debit_before=?,note='available balance decreased before withdrawal' WHERE id=?")){p.setLong(1,cents);p.setLong(2,available);p.setString(3,id.toString());p.executeUpdate();}return read(id);});}
    public CompletionStage<Loot> bankStart(UUID id,String expected,boolean incoming,long before){return storage.submit(()->{
        if(before<0)throw new IllegalArgumentException("Неверный баланс");try(var p=db().prepareStatement("UPDATE raid_loot SET state=?,"+(incoming?"credit_before":"debit_before")+"=?,note='' WHERE id=? AND state=?")){p.setString(1,incoming?"CREDITING":"DEBITING");p.setLong(2,before);p.setString(3,id.toString());p.setString(4,expected);if(p.executeUpdate()!=1)throw new IllegalArgumentException("Платёж уже начат");}return read(id);
    });}
    public CompletionStage<Loot> resolve(UUID id,String leg,boolean occurred,String actor,String reason){return storage.submit(()->{
        if(reason==null||reason.strip().length()<8||reason.length()>200)throw new IllegalArgumentException("Укажите основание сверки: 8–200 символов");
        var old=read(id);if(old==null)throw new IllegalArgumentException("Квитанция не найдена");
        String expected,next;
        switch(leg){case "core"->{expected="REVIEW_CORE";next=occurred?"BREACHED":"VOID";}case "debit"->{expected="REVIEW_DEBIT";next=occurred?"DEBITED":"PREPARED";}case "credit"->{expected="REVIEW_CREDIT";next=occurred?"FUNDED":"DEBITED";}default->throw new IllegalArgumentException("core | debit | credit");}
        if(!old.state().equals(expected))throw new IllegalArgumentException("Квитанция не ожидает сверки "+leg);
        if(leg.equals("core"))try(var p=db().prepareStatement("UPDATE hp SET hp=1,receipt='' WHERE key=?")){p.setString(1,old.core().id());p.executeUpdate();}
        try(var p=db().prepareStatement("UPDATE raid_loot SET state=?,note=? WHERE id=? AND state=?")){p.setString(1,next);p.setString(2,"admin "+actor+": "+reason);p.setString(3,id.toString());p.setString(4,expected);p.executeUpdate();}
        storage.audit(actor,old.attacker().toString(),old.defender().toString(),"LOOT_RESOLVE_"+leg.toUpperCase(Locale.ROOT),System.currentTimeMillis(),0);return read(id);
    });}
}
