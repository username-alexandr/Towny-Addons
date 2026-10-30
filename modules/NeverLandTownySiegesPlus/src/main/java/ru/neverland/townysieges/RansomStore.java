package ru.neverland.townysieges;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Immutable terms and two durable bank legs. No external bank call runs on the SQLite worker. */
public final class RansomStore {
    public record Offer(UUID id,UUID raid,UUID defender,UUID attacker,UUID proposer,UUID acceptor,
                        long cents,long created,long expires,String state,String note) {}
    private final RaidStore storage;
    public RansomStore(RaidStore storage){this.storage=storage;}
    static void initialize(Connection db)throws SQLException {
        try(var s=db.createStatement()){
            s.execute("CREATE TABLE IF NOT EXISTS raid_ransom(raid TEXT PRIMARY KEY,id TEXT NOT NULL UNIQUE,defender TEXT NOT NULL,attacker TEXT NOT NULL,proposer TEXT NOT NULL,acceptor TEXT NOT NULL,cents INTEGER NOT NULL,created INTEGER NOT NULL,expires INTEGER NOT NULL,state TEXT NOT NULL,note TEXT NOT NULL)");
            // An interrupted call cannot be retried: a provider may have changed the bank before stopping.
            s.execute("UPDATE raid_ransom SET state='REVIEW_DEBIT',note='Сервер остановился при списании: нужна сверка' WHERE state='DEBITING'");
            s.execute("UPDATE raid_ransom SET state='REVIEW_CREDIT',note='Сервер остановился при зачислении: нужна сверка' WHERE state='CREDITING'");
            s.execute("UPDATE raid_ransom SET state='REVIEW_REFUND',note='Сервер остановился при возврате: нужна сверка' WHERE state='REFUNDING'");
        }
    }
    static Offer read(Connection db,UUID raid)throws SQLException {
        try(var p=db.prepareStatement("SELECT * FROM raid_ransom WHERE raid=?")){
            p.setString(1,raid.toString());try(var r=p.executeQuery()){
                return r.next()?new Offer(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("raid")),UUID.fromString(r.getString("defender")),UUID.fromString(r.getString("attacker")),UUID.fromString(r.getString("proposer")),r.getString("acceptor").isBlank()?null:UUID.fromString(r.getString("acceptor")),r.getLong("cents"),r.getLong("created"),r.getLong("expires"),r.getString("state"),r.getString("note")):null;
            }
        }
    }
    static boolean blocking(Connection db,UUID raid)throws SQLException{
        var r=read(db,raid);return r!=null&&Set.of("ACCEPTED","DEBITING","REVIEW_DEBIT","DEBITED","CREDITING","REVIEW_CREDIT","FUNDED","REFUNDING","REVIEW_REFUND","REFUNDED").contains(r.state());
    }
    public CompletionStage<Offer> get(UUID raid){return storage.submit(()->read(storage.connection(),raid));}
    public CompletionStage<List<Offer>> pending(){return storage.submit(()->{
        var result=new ArrayList<Offer>();try(var s=storage.connection().createStatement();var r=s.executeQuery("SELECT raid FROM raid_ransom WHERE state NOT IN ('DONE','VOID') ORDER BY CASE WHEN state IN ('FUNDED','REFUNDED') THEN 0 WHEN state IN ('DEBITED','ACCEPTED','REFUNDING') THEN 1 WHEN state='OFFERED' THEN 2 ELSE 3 END,created ASC LIMIT 100")){
            while(r.next())result.add(read(storage.connection(),UUID.fromString(r.getString(1))));
        }return List.copyOf(result);
    });}
    private static RaidStore.Raid current(Connection db,UUID id)throws SQLException{
        try(var p=db.prepareStatement("SELECT * FROM raids WHERE id=?")){p.setString(1,id.toString());try(var r=p.executeQuery()){
            if(!r.next())throw new IllegalArgumentException("Рейд не найден");
            return new RaidStore.Raid(id,UUID.fromString(r.getString("attacker")),UUID.fromString(r.getString("defender")),UUID.fromString(r.getString("actor")),r.getLong("created"),r.getLong("starts"),r.getLong("ends"),r.getLong("next"),r.getString("state"));
        }}
    }
    public CompletionStage<Offer> propose(UUID raid,UUID defender,UUID proposer,long cents,long now,long offerMillis){
        if(raid==null||defender==null||proposer==null||cents<1||offerMillis<1000||now<0)throw new IllegalArgumentException("Неверные условия капитуляции");
        return storage.submit(()->storage.transaction(()->{
            var db=storage.connection();var war=current(db,raid);if(!war.defender().equals(defender)||!war.fighting(now)||!war.fighting(System.currentTimeMillis()))throw new IllegalArgumentException("Предложение доступно защищающемуся городу во время боя");
            var old=read(db,raid);if(old!=null&&!old.state().equals("VOID")&&!(old.state().equals("OFFERED")&&old.expires()<=now))throw new IllegalArgumentException("Предложение уже действует: "+old.id());
            long expires=Math.min(war.ends(),Math.addExact(now,offerMillis));var id=UUID.randomUUID();
            try(var p=db.prepareStatement("INSERT OR REPLACE INTO raid_ransom VALUES(?,?,?,?,?,?,?,?,?,?,?)")){
                p.setString(1,raid.toString());p.setString(2,id.toString());p.setString(3,defender.toString());p.setString(4,war.attacker().toString());p.setString(5,proposer.toString());p.setString(6,"");p.setLong(7,cents);p.setLong(8,now);p.setLong(9,expires);p.setString(10,"OFFERED");p.setString(11,"");p.executeUpdate();
            }
            storage.audit(proposer.toString(),war.attacker().toString(),defender.toString(),"RANSOM_OFFER",now,0);
            return read(db,raid);
        }));
    }
    public CompletionStage<Offer> accept(UUID raid,UUID id,UUID attacker,UUID actor,long now){return storage.submit(()->storage.transaction(()->{
        var db=storage.connection();var offer=read(db,raid);var war=current(db,raid);
        if(offer==null||!offer.id().equals(id)||!offer.attacker().equals(attacker)||!war.attacker().equals(attacker)||!war.fighting(now)||!war.fighting(System.currentTimeMillis())||!offer.state().equals("OFFERED")||now>=offer.expires())throw new IllegalArgumentException("Предложение больше недействительно");
        try(var p=db.prepareStatement("UPDATE raid_ransom SET state='ACCEPTED',acceptor=?,note='Нападающий согласился; ожидается перевод' WHERE raid=? AND id=? AND state='OFFERED'")){
            p.setString(1,actor.toString());p.setString(2,raid.toString());p.setString(3,id.toString());if(p.executeUpdate()!=1)throw new SQLException("Предложение изменилось");
        }
        storage.audit(actor.toString(),attacker.toString(),offer.defender().toString(),"RANSOM_ACCEPT",now,0);return read(db,raid);
    }));}
    public CompletionStage<Offer> change(UUID raid,UUID id,String expected,String next,String note){
        if(note==null||note.length()>240)throw new IllegalArgumentException("Неверная запись выкупа");
        return storage.submit(()->storage.transaction(()->{
            var db=storage.connection();
            if(expected.equals("ACCEPTED")&&next.equals("DEBITING")){
                var offer=read(db,raid);
                if(offer==null||offer.expires()<=System.currentTimeMillis()||!current(db,raid).fighting(System.currentTimeMillis()))throw new IllegalArgumentException("Срок предложения истёк до начала оплаты");
            }
            try(var p=db.prepareStatement("UPDATE raid_ransom SET state=?,note=? WHERE raid=? AND id=? AND state=?")){
                p.setString(1,next);p.setString(2,note);p.setString(3,raid.toString());p.setString(4,id.toString());p.setString(5,expected);
                if(p.executeUpdate()!=1)throw new IllegalArgumentException("Состояние выкупа изменилось");
            }var result=read(db,raid);storage.audit(result.acceptor()==null?result.proposer().toString():result.acceptor().toString(),result.attacker().toString(),result.defender().toString(),"RANSOM_"+next,System.currentTimeMillis(),0);return result;
        }));
    }
    public CompletionStage<Offer> expireOffer(UUID raid,UUID id,long now){return storage.submit(()->storage.transaction(()->{
        var db=storage.connection();var offer=read(db,raid);
        if(offer==null||!offer.id().equals(id)||!Set.of("OFFERED","ACCEPTED").contains(offer.state())||offer.expires()>now&&current(db,raid).ends()>now)return offer;
        try(var p=db.prepareStatement("UPDATE raid_ransom SET state='VOID',note='Срок предложения истёк до банковской операции' WHERE raid=? AND id=? AND state=?")){
            p.setString(1,raid.toString());p.setString(2,id.toString());p.setString(3,offer.state());p.executeUpdate();
        }return read(db,raid);
    }));}
    public CompletionStage<Offer> resolve(UUID raid,UUID id,String leg,boolean occurred,String actor,String reason){
        if(reason==null||reason.strip().length()<8||reason.length()>200)throw new IllegalArgumentException("Укажите основание сверки: 8–200 символов");
        String expected,next;
        if(leg.equals("debit")){expected="REVIEW_DEBIT";next=occurred?"DEBITED":"VOID";}
        else if(leg.equals("credit")){expected="REVIEW_CREDIT";next=occurred?"FUNDED":"DEBITED";}
        else if(leg.equals("refund")){expected="REVIEW_REFUND";next=occurred?"REFUNDED":"DEBITED";}
        else throw new IllegalArgumentException("Выберите debit, credit или refund");
        return change(raid,id,expected,next,"Сверка "+actor+": "+reason);
    }
    public CompletionStage<Offer> refund(UUID raid,UUID id,String admin,String reason){
        if(reason==null||reason.strip().length()<8||reason.length()>200)throw new IllegalArgumentException("Укажите причину возврата: 8–200 символов");
        return change(raid,id,"DEBITED","REFUNDING","Администратор "+admin+" запустил возврат: "+reason);
    }
}
