package ru.neverland.townyspecialization.production;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import static ru.neverland.townyspecialization.production.ProfileGoodsBook.*;

/** Used exclusively inside ProductionRepository's existing single SQLite worker and transaction. */
final class ProfileGoodsLedger {
    private static final UUID SYSTEM=new UUID(0,0);
    private final ProductionRepository owner;private final Connection db;
    ProfileGoodsLedger(ProductionRepository owner,Connection db){this.owner=owner;this.db=db;}
    static void upgrade(Connection db)throws SQLException{
        boolean received=false;try(var s=db.createStatement();var r=s.executeQuery("PRAGMA table_info(stock)")){while(r.next())if(r.getString("name").equals("received"))received=true;}
        db.setAutoCommit(false);try(var s=db.createStatement()){
            if(!received){
                s.execute("CREATE TABLE stock_v2(town TEXT NOT NULL,product TEXT NOT NULL REFERENCES products(id),available INTEGER NOT NULL CHECK(available>=0),reserved INTEGER NOT NULL CHECK(reserved>=0),produced INTEGER NOT NULL CHECK(produced>=0),received INTEGER NOT NULL DEFAULT 0 CHECK(received>=0),sent INTEGER NOT NULL DEFAULT 0 CHECK(sent>=0),CHECK(produced+received>=available+reserved+sent),PRIMARY KEY(town,product))");
                s.execute("INSERT INTO stock_v2(town,product,available,reserved,produced) SELECT town,product,available,reserved,produced FROM stock");
                s.execute("DROP TABLE stock");s.execute("ALTER TABLE stock_v2 RENAME TO stock");
            }
            s.execute("CREATE TABLE IF NOT EXISTS seasonal_entries(id TEXT PRIMARY KEY,kind TEXT NOT NULL,owner TEXT NOT NULL,other TEXT NOT NULL,world TEXT NOT NULL,period TEXT NOT NULL,terms TEXT NOT NULL,state TEXT NOT NULL CHECK(state IN ('HELD','SWAPPED','CANCELLED','EXPIRED')),receipt TEXT UNIQUE,actor TEXT NOT NULL,actor_name TEXT NOT NULL,reason TEXT NOT NULL,created INTEGER NOT NULL,expires INTEGER NOT NULL,settled INTEGER NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS seasonal_owner ON seasonal_entries(owner,created)");s.execute("CREATE INDEX IF NOT EXISTS seasonal_other ON seasonal_entries(other,created)");s.execute("CREATE INDEX IF NOT EXISTS seasonal_due ON seasonal_entries(state,expires)");s.execute("PRAGMA user_version=2");db.commit();
        }catch(SQLException e){try{db.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}throw e;}finally{db.setAutoCommit(true);}
    }
    Terms quote(UUID id,UUID town,UUID other,UUID world,String period,String title,Map<String,Long> give,Map<String,Long> receive,UUID actor,String actorName,long expires,long now,String townName,String otherName)throws Exception{
        give=goods(give,false);receive=goods(receive,other==null);var all=new TreeSet<>(give.keySet());all.addAll(receive.keySet());var labels=new LinkedHashMap<String,String>();
        for(String key:all)try(var s=db.prepareStatement("SELECT title FROM products WHERE id=?")){s.setString(1,key);try(var r=s.executeQuery()){if(!r.next())throw new IllegalArgumentException("Одна из нужных партий ещё не выпускалась; сначала подготовьте продукцию для этой сметы");labels.put(key,r.getString(1));}}
        var terms=new Terms(id,town,other,townName,otherName,world,period,title,give,receive,labels,actor,actorName,now,expires);if(entry(id)!=null)throw new IllegalArgumentException("Предложение уже сохранено; откройте его запись");available(town,give);return terms;
    }
    Entry confirm(Terms terms,ProductionCatalog.Limits limits,long now)throws Exception{
        var old=entry(terms.id());if(old!=null){if(!old.terms().equals(terms))throw new IllegalArgumentException("Идентификатор уже занят другой сметой");return old;}
        if(now>=terms.expires()||now<terms.created()||now-terms.created()>120_000)throw new IllegalArgumentException("Подтверждение устарело; проверьте смету заново");
        if(owner.scalar("SELECT COUNT(*) FROM seasonal_entries")>=100000)throw new IllegalArgumentException("Журнал сезонных резервов заполнен; новые сметы закрыты");
        if(owner.scalar("SELECT COUNT(*) FROM seasonal_entries WHERE owner=? AND state='HELD'",terms.town().toString())>=8||owner.scalar("SELECT COUNT(*) FROM seasonal_entries WHERE state='HELD'")>=1024)throw new IllegalArgumentException("Достигнут предел действующих сезонных резервов");
        if(terms.other()==null&&owner.scalar("SELECT COUNT(*) FROM seasonal_entries WHERE owner=? AND kind='PREPARATION' AND state='HELD'",terms.town().toString())>0)throw new IllegalArgumentException("У города уже отложен сезонный запас; сначала освободите его");
        if(owner.capacity(terms.town())>limits.goodsCity()||owner.capacity(null)>limits.goodsServer())throw new IllegalArgumentException("Каталог превышает текущий предел запасов");
        available(terms.town(),terms.give());for(var e:terms.give().entrySet())change("UPDATE stock SET available=available-?,reserved=reserved+? WHERE town=? AND product=? AND available>=?",e.getValue(),e.getValue(),terms.town().toString(),e.getKey(),e.getValue());
        String reason=terms.other()==null?"Запас отложен до указанного срока; сезонные коэффициенты прежние":"Мэр подтвердил точный обмен; его продукция отложена до ответа другого города";
        owner.exec("INSERT INTO seasonal_entries VALUES(?,?,?,?,?,?,?,'HELD',NULL,?,?,?,?,?,0)",terms.id().toString(),terms.kind(),terms.town().toString(),terms.other()==null?"":terms.other().toString(),terms.world().toString(),terms.period(),encode(terms),terms.actor().toString(),terms.actorName(),reason,terms.created(),terms.expires());
        owner.log(terms.other()==null?"season-preparation-held":"season-offer-held",terms.id().toString(),terms.town(),terms.actorName(),reason+"; "+description(terms));return entry(terms.id());
    }
    Entry entry(UUID id)throws Exception{
        try(var s=db.prepareStatement("SELECT * FROM seasonal_entries WHERE id=?")){s.setString(1,id.toString());try(var r=s.executeQuery()){
            if(!r.next())return null;try{Terms t=decode(r.getString("terms"));if(!id.equals(t.id())||!t.kind().equals(r.getString("kind"))||!t.town().toString().equals(r.getString("owner"))||!Objects.equals(t.other()==null?"":t.other().toString(),r.getString("other"))||!t.world().toString().equals(r.getString("world"))||!t.period().equals(r.getString("period"))||t.created()!=r.getLong("created")||t.expires()!=r.getLong("expires"))throw new IllegalArgumentException("Смета и индекс не совпадают");String receipt=r.getString("receipt");return new Entry(t,r.getString("state"),receipt==null?null:UUID.fromString(receipt),UUID.fromString(r.getString("actor")),r.getString("actor_name"),r.getString("reason"),r.getLong("settled"));}catch(Exception invalid){throw new IOException("Повреждена запись сезонного резерва "+id,invalid);}
        }}
    }
    List<Entry> entries(UUID town,int page)throws Exception{ProductionRepository.page(page);var ids=new ArrayList<UUID>();try(var s=db.prepareStatement("SELECT id FROM seasonal_entries WHERE owner=? OR other=? ORDER BY created DESC,id LIMIT 20 OFFSET ?")){s.setString(1,town.toString());s.setString(2,town.toString());s.setLong(3,(page-1L)*20);try(var r=s.executeQuery()){while(r.next())ids.add(UUID.fromString(r.getString(1)));}}var result=new ArrayList<Entry>();for(UUID id:ids)result.add(entry(id));return List.copyOf(result);}
    List<Entry> active()throws Exception{var ids=new ArrayList<UUID>();try(var s=db.createStatement();var r=s.executeQuery("SELECT id FROM seasonal_entries WHERE state='HELD' ORDER BY expires,id LIMIT 1024")){while(r.next())ids.add(UUID.fromString(r.getString(1)));}var rows=new ArrayList<Entry>();for(UUID id:ids)rows.add(entry(id));return List.copyOf(rows);}
    Entry accept(UUID id,UUID receipt,UUID town,UUID actor,String actorName,String hash,ProductionCatalog.Limits limits,long now)throws Exception{
        Objects.requireNonNull(receipt);text(actorName,64);var row=required(id);var t=row.terms();if(t.other()==null||!t.other().equals(town)||!t.hash().equals(hash))throw new IllegalArgumentException("Другой город или условия обмена; откройте подтверждение заново");
        try(var s=db.prepareStatement("SELECT id FROM seasonal_entries WHERE receipt=?")){s.setString(1,receipt.toString());try(var r=s.executeQuery()){if(r.next()&&!id.toString().equals(r.getString(1)))throw new IllegalArgumentException("Квитанция уже относится к другому обмену");}}
        if(row.state().equals("SWAPPED"))return row;if(!row.state().equals("HELD"))throw new IllegalArgumentException("Предложение закрыто; продукция не списана");
        if(now>=t.expires())return release(row,"EXPIRED",SYSTEM,"Система","Срок предложения закончился; резерв возвращён",now);
        long give=total(t.give()),receive=total(t.receive());long afterOwner=Math.addExact(Math.subtractExact(owner.capacity(t.town()),give),receive),afterOther=Math.addExact(Math.subtractExact(owner.capacity(t.other()),receive),give);
        if(afterOwner>limits.goodsCity()||afterOther>limits.goodsCity()||owner.capacity(null)>limits.goodsServer())throw new IllegalArgumentException("После обмена не хватит места в каталоге одного из городов; учтены незавершённые партии");
        available(t.other(),t.receive());for(var e:t.give().entrySet()){change("UPDATE stock SET reserved=reserved-?,sent=sent+? WHERE town=? AND product=? AND reserved>=?",e.getValue(),e.getValue(),t.town().toString(),e.getKey(),e.getValue());incoming(t.other(),e.getKey(),e.getValue());}
        for(var e:t.receive().entrySet()){change("UPDATE stock SET available=available-?,sent=sent+? WHERE town=? AND product=? AND available>=?",e.getValue(),e.getValue(),t.other().toString(),e.getKey(),e.getValue());incoming(t.town(),e.getKey(),e.getValue());}
        String reason="Оба мэра подтвердили точный обмен; две стороны и квитанция сохранены вместе";owner.exec("UPDATE seasonal_entries SET state='SWAPPED',receipt=?,actor=?,actor_name=?,reason=?,settled=? WHERE id=?",receipt.toString(),actor.toString(),actorName,reason,now,id.toString());
        owner.log("season-swap",id+":"+t.town(),t.town(),actorName,reason+"; "+description(t));owner.log("season-swap",id+":"+t.other(),t.other(),actorName,reason+"; "+description(t));return entry(id);
    }
    Entry cancel(UUID id,UUID town,UUID actor,String actorName,String reason,boolean admin,long now)throws Exception{
        text(reason,300);if(reason.trim().length()<8)throw new IllegalArgumentException("Укажите причину не короче 8 символов");var row=required(id);
        if(!admin&&!row.terms().town().equals(town))throw new IllegalArgumentException("Резерв освобождает мэр города, который его создал");if(row.state().equals("SWAPPED"))throw new IllegalArgumentException("Подтверждённый обмен нельзя отменить; партии уже переданы");if(!row.state().equals("HELD"))return row;
        return release(row,"CANCELLED",actor,actorName,reason,now);
    }
    private Entry release(Entry row,String state,UUID actor,String actorName,String reason,long now)throws Exception{
        text(actorName,64);var t=row.terms();for(var e:t.give().entrySet())change("UPDATE stock SET reserved=reserved-?,available=available+? WHERE town=? AND product=? AND reserved>=?",e.getValue(),e.getValue(),t.town().toString(),e.getKey(),e.getValue());
        owner.exec("UPDATE seasonal_entries SET state=?,actor=?,actor_name=?,reason=?,settled=? WHERE id=?",state,actor.toString(),actorName,reason,now,t.id().toString());owner.log("season-"+state.toLowerCase(Locale.ROOT),t.id().toString(),t.town(),actorName,reason+"; "+description(t));return entry(t.id());
    }
    int expire(Map<String,String> periods,long now)throws Exception{
        var ids=new ArrayList<UUID>();try(var s=db.prepareStatement("SELECT id FROM seasonal_entries WHERE state='HELD' ORDER BY expires,id LIMIT 1024");var r=s.executeQuery()){while(r.next())ids.add(UUID.fromString(r.getString(1)));}
        int count=0;for(UUID id:ids){var row=required(id);var t=row.terms();String current=periods.get(t.world().toString());boolean changed=t.other()!=null&&current!=null&&!current.equals(t.period());if(now>=t.expires()||changed){release(row,"EXPIRED",SYSTEM,"Система",changed?"Ярмарка этого сезона закрыта; резерв возвращён":"Срок резерва закончился; продукция возвращена",now);if(++count>=50)break;}}return count;
    }
    private Entry required(UUID id)throws Exception{var row=entry(id);if(row==null)throw new IllegalArgumentException("Сезонная запись не найдена");return row;}
    ru.neverland.core.AuditRecord audit(String id,String kind,String operation,UUID historyTown,String actorName,String reason,long at)throws Exception{
        var row=required(UUID.fromString(operation.split(":",2)[0]));var t=row.terms();boolean swap=kind.equals("season-swap"),created=kind.endsWith("-held");UUID actor=created?t.actor():row.actor();
        var who=actor.equals(SYSTEM)?new ru.neverland.core.AuditRecord.Party(actorName.equals("Система")?"SYSTEM":"CONSOLE","",actorName,""):new ru.neverland.core.AuditRecord.Party("PLAYER",actor.toString(),actorName,created?t.town().toString():swap?t.other().toString():"");
        var source=new ru.neverland.core.AuditRecord.Party("TOWN",t.town().toString(),t.townName(),t.town().toString());var target=t.other()==null?source:new ru.neverland.core.AuditRecord.Party("TOWN",t.other().toString(),t.otherName(),t.other().toString());
        Map<String,Long> amounts=t.give();if(swap&&historyTown.equals(t.other())){var exchanged=source;source=target;target=exchanged;amounts=t.receive();}
        String outcome=swap?"SWAPPED":created?"HELD":kind.equals("season-expired")?"EXPIRED":"CANCELLED";
        return new ru.neverland.core.AuditRecord(id,at,"NeverLandTownySpecialization",t.other()==null?"CITY_SEASON_STOCK":"CITY_SEASON_FAIR",outcome,t.id().toString(),who,source,target,String.join(",",new TreeSet<>(amounts.keySet())),total(amounts),"",reason+(swap?"; эта сторона передала "+amounts:""));
    }
    private void available(UUID town,Map<String,Long> goods)throws SQLException{for(var e:goods.entrySet())if(owner.scalar("SELECT available FROM stock WHERE town=? AND product=?",town.toString(),e.getKey())<e.getValue())throw new IllegalArgumentException("Не хватает свободных партий: "+productTitle(e.getKey())+"; проверьте запас города");}
    private String productTitle(String product)throws SQLException{try(var s=db.prepareStatement("SELECT title FROM products WHERE id=?")){s.setString(1,product);try(var r=s.executeQuery()){return r.next()?r.getString(1):"нужная продукция";}}}
    private void incoming(UUID town,String product,long amount)throws SQLException{owner.exec("INSERT INTO stock(town,product,available,reserved,produced,received,sent) VALUES(?,?,?,0,0,?,0) ON CONFLICT(town,product) DO UPDATE SET available=available+excluded.available,received=received+excluded.received",town.toString(),product,amount,amount);}
    private void change(String sql,Object...args)throws SQLException{try(var s=db.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);if(s.executeUpdate()!=1)throw new SQLException("Подтверждённый резерв продукции не совпадает с каталогом");}}
    private static long total(Map<String,Long> goods){long total=0;for(long n:goods.values())total=Math.addExact(total,n);return total;}
    private static String description(Terms t){return "город "+t.townName()+" ("+t.town()+")"+(t.other()==null?"":" ↔ "+t.otherName()+" ("+t.other()+")")+"; отдаёт "+t.give()+"; получает "+t.receive()+"; период "+t.period()+"; смета "+t.hash();}
}
