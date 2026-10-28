package ru.neverland.mintcontracts.orders;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.sql.*;

/** Project demand, actual supplied materials and reservations share one durable transaction authority. */
public final class ServerProjectsSmoke {
    static int checks;static void check(boolean v,String label){checks++;if(!v)throw new AssertionError(label);}
    static <T>T waitFor(CompletionStage<T> stage)throws Exception{return stage.toCompletableFuture().get(5,TimeUnit.SECONDS);}
    static void denied(CompletionStage<?> stage,String label)throws Exception{try{waitFor(stage);}catch(ExecutionException expected){checks++;return;}throw new AssertionError(label);}
    static void supply(ServerMaterials db,String material,int quantity)throws Exception{var d=new ServerMaterials.Delivery(UUID.randomUUID(),UUID.randomUUID(),"builder","resource_test",material,quantity,1000,"COMMON",System.currentTimeMillis());waitFor(db.prepare(d));waitFor(db.confirm(d.id(),"inventory saved durably"));}
    static void close(ServerMaterials db)throws Exception{db.close();waitFor(db.closed());}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("server-projects-");Path file=root.resolve("resources.db");var db=new ServerMaterials(file);waitFor(db.ready());
        var costs=Map.of("STONE",100L,"OAK_LOG",50L);waitFor(db.plan("spawn","Площадь спавна","Admin",costs));waitFor(db.plan("spawn","Площадь спавна","Admin",costs));
        waitFor(db.plan("road","Дорога спавна","Admin",Map.of("STONE",200L)));
        check(waitFor(db.projects(1)).size()==2,"planning retry does not duplicate projects");
        check(waitFor(db.demand()).equals(Map.of("STONE",300L,"OAK_LOG",50L)),"unfunded plans create demand without creating resources");
        denied(db.plan("spawn","Площадь спавна","Admin",Map.of("STONE",999L)),"existing quote cannot silently change");
        supply(db,"STONE",150);check(waitFor(db.demand()).get("STONE")==150,"confirmed stock reduces total demand across projects once");
        denied(db.reserveProject("spawn","Admin","start actual building"),"missing wood prevents whole project reservation");
        check(waitFor(db.balances()).get(0).available()==150&&waitFor(db.projectHistory("spawn")).size()==1,"failed reserve rolls back stock, project state and history");
        supply(db,"OAK_LOG",50);var reservation=waitFor(db.reserveProject("spawn","Admin","all materials supplied"));
        check(reservation.id().equals(ServerMaterials.projectReservation("spawn")),"project owns stable reservation ID");
        check(waitFor(db.reserveProject("spawn","OtherAdmin","retry existing operation")).id().equals(reservation.id()),"reservation retry never deducts materials twice");
        check(waitFor(db.demand()).equals(Map.of("STONE",150L)),"allocated project leaves demand and reserved stock cannot fund another plan");
        waitFor(db.allocation(reservation.id(),"consume","actual spawn stage built"));denied(db.allocation(reservation.id(),"return","cannot return spent resources"),"spent resource cannot be reissued");
        waitFor(db.cancelProject("road","Moderator","road cancelled before build"));check(waitFor(db.demand()).isEmpty(),"cancelled plan removes demand without destroying available materials");
        check(waitFor(db.projectHistory("road")).stream().anyMatch(s->s.contains("Moderator")&&s.contains("road cancelled")),"cancellation actor and reason commit together with state");
        close(db);
        // Simulate an installed v1 warehouse with all its prior receipts still present.
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()){s.execute("PRAGMA user_version=1");}
        db=new ServerMaterials(file);waitFor(db.ready());check(waitFor(db.demand()).isEmpty()&&waitFor(db.projects(1)).size()==2,"schema upgrade and restart preserve projects and completion");
        waitFor(db.plan("garden","Сад спавна","Admin",Map.of("STONE",250L)));check(waitFor(db.demand()).get("STONE")==200,"new project uses only remaining physical stock");
        close(db);

        var stone=new PublicOrders.Template("stone","Камень",PublicOrders.Type.DELIVERY,"STONE",10,1000,60000,OrderRarity.COMMON);
        var wood=new PublicOrders.Template("wood","Дерево",PublicOrders.Type.DELIVERY,"OAK_LOG",10,1000,60000,OrderRarity.COMMON);
        var priority=new DemandPriority();priority.update(Map.of("STONE",1000L));check(priority.factor(stone)==5&&priority.factor(wood)==1,"demand priority is bounded and material specific");
        var weights=new EnumMap<OrderRarity,Integer>(OrderRarity.class);for(var rarity:OrderRarity.values())weights.put(rarity,1);
        Clock clock=Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"),ZoneOffset.UTC);Random random=new Random(650);int preferred=0,baseline=0;
        for(int n=0;n<100;n++){
            var board=new DailyOrderBoard(root.resolve("priority-"+n+".yml"),List.of(stone,wood),1,weights,clock,random).priority(priority::factor);
            if(board.snapshot().templates().get(0).id().equals("stone"))preferred++;
            var normal=new DailyOrderBoard(root.resolve("normal-"+n+".yml"),List.of(stone,wood),1,weights,clock,random);
            if(normal.snapshot().templates().get(0).id().equals("stone"))baseline++;
        }
        check(preferred>=70&&preferred>baseline+15,"new personal draws actually prefer the missing material");
        Path boardFile=root.resolve("retained.yml");var board=new DailyOrderBoard(boardFile,List.of(stone,wood),1,weights,clock,random).priority(priority::factor);var accepted=board.snapshot();priority.update(Map.of("OAK_LOG",1000L));
        check(board.snapshot().equals(accepted)&&new DailyOrderBoard(boardFile,List.of(stone,wood),1,weights,clock,random).priority(priority::factor).snapshot().equals(accepted),"demand changes and restart cannot reroll today's saved quotes");
        check(accepted.templates().get(0).cents()==1000,"selection priority never changes reward or rarity");
        System.out.println("ServerProjectsSmoke PASS "+checks+" checks");
    }
}
