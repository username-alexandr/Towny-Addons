package ru.neverland.townysieges;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletionStage;
/** Verified raid damage survives physical destruction and never regenerates on a timer. */
public final class BuildingDamageStore {
    public record Damage(RaidStore.Key key,UUID town,String project,String material,double maximum,double remaining,String version,String pending,long at){}
    private final RaidStore storage;
    public BuildingDamageStore(RaidStore storage){this.storage=storage;}
    static void initialize(Connection db)throws SQLException{try(var s=db.createStatement()){
        s.execute("CREATE TABLE IF NOT EXISTS building_damage(key TEXT PRIMARY KEY,town TEXT NOT NULL,project TEXT NOT NULL,material TEXT NOT NULL,maximum REAL NOT NULL,remaining REAL NOT NULL,version TEXT NOT NULL,pending TEXT NOT NULL,at INTEGER NOT NULL)");
        s.execute("CREATE INDEX IF NOT EXISTS building_damage_town ON building_damage(town,project)");
        s.execute("UPDATE building_damage SET remaining=1,pending='' WHERE pending<>''");
    }}
    static Damage read(Connection db,String key)throws SQLException{try(var p=db.prepareStatement("SELECT * FROM building_damage WHERE key=?")){p.setString(1,key);try(var r=p.executeQuery()){return r.next()?row(r):null;}}}
    static Damage row(ResultSet r)throws SQLException{var parts=r.getString("key").split(":");return new Damage(new RaidStore.Key(UUID.fromString(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),Integer.parseInt(parts[3])),UUID.fromString(r.getString("town")),r.getString("project"),r.getString("material"),r.getDouble("maximum"),r.getDouble("remaining"),r.getString("version"),r.getString("pending"),r.getLong("at"));}
    static void hit(Connection db,RaidStore.Hit hit,double hp,String receipt,UUID blast,long at)throws SQLException{if(hit.project().isEmpty())return;try(var p=db.prepareStatement("INSERT OR REPLACE INTO building_damage VALUES(?,?,?,?,?,?,?,?,?)")){p.setString(1,hit.key().id());p.setString(2,hit.town().toString());p.setString(3,hit.project());p.setString(4,hit.material());p.setDouble(5,hit.max());p.setDouble(6,hp);p.setString(7,blast+":"+hit.generation());p.setString(8,receipt);p.setLong(9,at);p.executeUpdate();}}
    static void finish(Connection db,RaidStore.Result result,boolean destroyed)throws SQLException{try(var p=db.prepareStatement("UPDATE building_damage SET remaining=?,pending='' WHERE key=? AND pending=? AND pending<>''")){p.setDouble(1,destroyed?0:1);p.setString(2,result.hit().key().id());p.setString(3,result.receipt());p.executeUpdate();}}
    static void reset(Connection db,String key)throws SQLException{try(var p=db.prepareStatement("DELETE FROM building_damage WHERE key=?")){p.setString(1,key);p.executeUpdate();}}
    public CompletionStage<List<Damage>> list(UUID town,String project){return storage.submit(()->{var out=new ArrayList<Damage>();try(var p=storage.connection().prepareStatement("SELECT * FROM building_damage WHERE town=? AND project=? ORDER BY key")){p.setString(1,town.toString());p.setString(2,project);try(var r=p.executeQuery()){while(r.next())out.add(row(r));}}return List.copyOf(out);});}
}
