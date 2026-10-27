package ru.neverland.mintcontracts.orders;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Real SQLite transactions, concurrency, restart, terminal IDs, rollback and corrupt-file refusal. */
public final class ServerMaterialsSmoke {
    private static int checks;
    private static <T> T await(CompletionStage<T> stage)throws Exception{return stage.toCompletableFuture().get(10,TimeUnit.SECONDS);}
    private static void ok(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void fails(CompletionStage<?> operation,String label)throws Exception{try{await(operation);}catch(ExecutionException expected){checks++;return;}throw new AssertionError(label);}
    private static ServerMaterials store(Path file)throws Exception{var db=new ServerMaterials(file);await(db.ready());return db;}
    private static void close(ServerMaterials db)throws Exception{db.close();await(db.closed());}
    private static ServerMaterials.Delivery delivery(String material,int amount){return new ServerMaterials.Delivery(UUID.randomUUID(),UUID.randomUUID(),"=unsafe_name","build_test",material,amount,1000,"COMMON",System.currentTimeMillis());}
    private static long available(ServerMaterials db,String material)throws Exception{return await(db.balances()).stream().filter(r->r.material().equals(material)).mapToLong(ServerMaterials.Balance::available).findFirst().orElse(0);}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("server-materials-");Path file=root.resolve("materials.db");ServerMaterials db=store(file);
        var wood=delivery("OAK_LOG",64);await(db.prepare(wood));ok(await(db.balances()).isEmpty(),"prepared inventory intent is not spendable stock");
        var jobs=new ArrayList<CompletableFuture<?>>();
        var firstDatabase=db;
        for(int n=0;n<100;n++)jobs.add(db.prepare(wood).thenCompose(d->firstDatabase.confirm(d.id(),"player receipt durably saved")).toCompletableFuture());
        await(CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new)));
        ok(available(db,"OAK_LOG")==64&&await(db.history(1)).size()==1,"100 overlapping retries credit one receipt only once");
        var changed=new ServerMaterials.Delivery(wood.id(),wood.player(),wood.template(),"STONE",64,1000,"COMMON",wood.created());
        fails(db.prepare(changed),"id cannot change material");ok(db.available(),"ordinary business rejection does not poison database");
        UUID reserve=UUID.randomUUID();await(db.allocate(reserve,"spawn","admin","reserve for spawn",Map.of("OAK_LOG",32L)));
        await(db.allocate(reserve,"spawn","admin","reserve for spawn",Map.of("OAK_LOG",32L)));
        ok(available(db,"OAK_LOG")==32,"repeated allocation is idempotent");
        fails(db.allocate(reserve,"other","admin","reserve for spawn",Map.of("OAK_LOG",32L)),"allocation cannot change project");
        fails(db.allocate(UUID.randomUUID(),"spawn","admin","not enough stone",Map.of("OAK_LOG",16L,"STONE",1L)),"mixed-material shortage rolls back entire reserve");
        ok(available(db,"OAK_LOG")==32&&await(db.allocations(1)).size()==1,"failed multi-material reserve changes neither stock nor history");
        await(db.allocation(reserve,"return","admin: cancelled spawn stage"));await(db.allocation(reserve,"return","admin: cancelled spawn stage"));
        ok(available(db,"OAK_LOG")==64,"return restores stock once");
        fails(db.allocation(reserve,"consume","admin: already returned"),"returned allocation cannot later consume stock");
        var reserves=new ArrayList<CompletableFuture<?>>();var accepted=new java.util.concurrent.atomic.AtomicInteger();
        for(int n=0;n<32;n++)reserves.add(db.allocate(UUID.randomUUID(),"spawn","admin","parallel reserve",Map.of("OAK_LOG",4L)).handle((r,e)->{if(e==null)accepted.incrementAndGet();return null;}).toCompletableFuture());
        await(CompletableFuture.allOf(reserves.toArray(CompletableFuture[]::new)));ok(accepted.get()==16&&available(db,"OAK_LOG")==0,"parallel reserve cannot overspend or make a negative balance");
        var held=await(db.allocations(1)).stream().filter(a->a.state().equals("RESERVED")).findFirst().orElseThrow();
        await(db.allocation(held.id(),"consume","admin: stage completed"));await(db.allocation(held.id(),"consume","admin: stage completed"));
        fails(db.allocation(held.id(),"return","admin: invalid refund"),"spent resource cannot be returned and used twice");
        var stone=delivery("STONE",128);await(db.prepare(stone));await(db.confirm(stone.id(),"saved inventory receipt"));await(db.settled(stone.id()));
        var abandoned=delivery("BIRCH_LOG",64);await(db.prepare(abandoned));await(db.voidDelivery(abandoned.id(),"admin confirmed inventory untouched"));
        fails(db.confirm(abandoned.id(),"must never create resources"),"void receipt cannot become stock");
        ok(available(db,"BIRCH_LOG")==0,"unconfirmed items are never invented");
        Path csv=await(db.export());String exported=Files.readString(csv);ok(exported.contains("player_name")&&exported.contains("'=unsafe_name")&&exported.contains(wood.id().toString()),"CSV keeps attribution and protects spreadsheet formulas");
        var before=await(db.balances());close(db);db=store(file);
        ok(await(db.balances()).equals(before),"stock survives a closed connection and full repository restart");
        ok(await(db.delivery(wood.id())).playerName().equals("=unsafe_name"),"player snapshot and delivery attribution survive restart");
        ok(await(db.unsettled()).stream().noneMatch(d->d.id().equals(stone.id())),"settled receipt does not reenter recovery queue");
        await(db.confirm(wood.id(),"duplicate after restart"));ok(available(db,"OAK_LOG")==0,"restart cannot re-credit a spent delivery");close(db);
        Path corrupt=root.resolve("broken.db");byte[] bytes="not a sqlite database".getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(corrupt,bytes);var broken=new ServerMaterials(corrupt);fails(broken.ready(),"corrupt database fails closed");ok(Arrays.equals(bytes,Files.readAllBytes(corrupt)),"corrupt original is not replaced with an empty database");close(broken);
        Path future=root.resolve("future.db");try(var c=DriverManager.getConnection("jdbc:sqlite:"+future);var s=c.createStatement()){s.execute("PRAGMA user_version=99");}var newer=new ServerMaterials(future);fails(newer.ready(),"unknown schema rejected");close(newer);
        var closed=store(root.resolve("closed.db"));close(closed);fails(closed.prepare(delivery("OAK_LOG",1)),"shutdown rejects new tasks without blocking caller");
        System.out.println("ServerMaterialsSmoke PASS: "+checks+" checks; real SQLite, duplicate receipts, stock conservation, project reserves, rollback, CSV, restart and corrupt storage");
    }
}
