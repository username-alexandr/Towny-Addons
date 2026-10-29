package ru.neverland.mintcontracts.orders;
import java.nio.file.*;import java.sql.*;import java.util.*;import java.util.concurrent.*;
/** Real SQLite close acknowledgement, blocked checkpoint and recovery; no fake driver or ignored close failure. */
public final class ServerMaterialsShutdownSmoke {
    static <T>T done(CompletionStage<T> value)throws Exception{return value.toCompletableFuture().get(10,TimeUnit.SECONDS);}static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void add(ServerMaterials db,int amount)throws Exception{var d=new ServerMaterials.Delivery(UUID.randomUUID(),UUID.randomUUID(),"fixture","STONE",amount,100,"COMMON",1);done(db.prepare(d));done(db.confirm(d.id(),"explicit saved test inventory"));}
    public static void main(String[]args)throws Exception{
        Path folder=Files.createTempDirectory("materials-checkpoint-");Path file=folder.resolve("materials.db");var db=new ServerMaterials(file);done(db.ready());add(db,40);db.close();done(db.closed());check(!Files.exists(Path.of(file+"-wal"))||Files.size(Path.of(file+"-wal"))==0,"acknowledged stop leaves no nonempty WAL");
        try(var c=DriverManager.getConnection("jdbc:sqlite:file:"+file+"?immutable=1");var s=c.createStatement();var r=s.executeQuery("SELECT received,available FROM balances WHERE material='STONE'")){check(r.next()&&r.getInt(1)==40&&r.getInt(2)==40,"main file alone contains all acknowledged deliveries");}
        db=new ServerMaterials(file);done(db.ready());try(var reader=DriverManager.getConnection("jdbc:sqlite:"+file)){reader.setAutoCommit(false);try(var s=reader.createStatement();var r=s.executeQuery("SELECT * FROM balances")){check(r.next(),"long-running reader holds real WAL snapshot");add(db,7);db.close();boolean failed=false;try{done(db.closed());}catch(ExecutionException expected){failed=true;}check(failed,"blocked truncation returns failed shutdown receipt rather than claiming durable stop");}reader.rollback();}
        db=new ServerMaterials(file);done(db.ready());check(done(db.balances()).get(0).available()==47,"checkpoint refusal loses no committed stock");db.close();done(db.closed());check(!Files.exists(Path.of(file+"-wal"))||Files.size(Path.of(file+"-wal"))==0,"unblocked retry confirms empty WAL");System.out.println("ServerMaterialsShutdownSmoke PASS: verified truncate/main-file persistence, real blocked checkpoint refusal and recovery");
    }
}
