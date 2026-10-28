package ru.neverland.mintcontracts.fishing;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Original SQLite WAL, overlapping radii, durable reservations and monotonic regeneration. */
public final class FishStockSmoke {
    private static int checks;
    private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static <T>T await(CompletionStage<T> value)throws Exception{return value.toCompletableFuture().get(10,TimeUnit.SECONDS);}
    public static void main(String[] args)throws Exception{
        Path directory=Files.createTempDirectory("fish-stock");UUID world=UUID.randomUUID(),player=UUID.randomUUID();
        var point=new FishStock.Point(world,15,15);var settings=new FishingSettings(true,false,true,24,3,60000,15000);long now=1800000000000L;
        var stock=new FishStock(directory.resolve("stock.db"));await(stock.ready());ok(stock.available(),"database ready");
        var operations=new ArrayList<CompletionStage<FishStock.Decision>>();
        for(int i=0;i<40;i++)operations.add(stock.reserve(UUID.randomUUID(),UUID.randomUUID(),point,settings,now));
        var rows=new ArrayList<FishStock.Decision>();for(var operation:operations)rows.add(await(operation));
        ok(rows.stream().filter(FishStock.Decision::reserved).count()==3,"concurrent fishermen cannot exceed shared stock");
        var first=rows.stream().filter(FishStock.Decision::reserved).findFirst().orElseThrow();
        ok(await(stock.reserve(first.id(),first.player(),point,settings,now)).equals(first),"reservation replay uses same receipt");
        try{await(stock.reserve(first.id(),player,new FishStock.Point(world,100,100),settings,now));throw new AssertionError("foreign reservation accepted");}catch(ExecutionException expected){checks++;}
        ok(!await(stock.reserve(UUID.randomUUID(),player,new FishStock.Point(world,16,15),settings,now)).reserved(),"crossing chunk edge cannot refill fish");
        ok(!await(stock.reserve(UUID.randomUUID(),player,new FishStock.Point(world,30,30),settings,now)).reserved(),"diagonal nearby casts share depleted area");
        ok(await(stock.reserve(UUID.randomUUID(),player,new FishStock.Point(world,80,15),settings,now)).reserved(),"moving to another part of water permits fishing");
        ok(await(stock.reserve(UUID.randomUUID(),player,new FishStock.Point(UUID.randomUUID(),15,15),settings,now)).reserved(),"worlds have independent stock");
        var depleted=await(stock.inspect(player,point,settings,now));ok(depleted.remaining()==0&&depleted.retryAt()>=now+20000&&depleted.retryAt()<now+21000,"gradual regeneration computes next available catch");
        ok(!await(stock.reserve(UUID.randomUUID(),player,point,settings,now+19999)).reserved(),"cannot catch before enough stock regenerates");
        ok(await(stock.reserve(UUID.randomUUID(),player,point,settings,now+21000)).reserved(),"next catch unlocks at regeneration boundary");
        ok(await(stock.finish(first.id(),true)).state().equals("CAUGHT"),"successful fish committed");
        ok(await(stock.finish(first.id(),false)).state().equals("CAUGHT"),"late cancellation cannot refund committed fish");
        var other=rows.stream().filter(FishStock.Decision::reserved).filter(r->!r.id().equals(first.id())).findFirst().orElseThrow();
        ok(await(stock.finish(other.id(),false)).state().equals("RELEASED"),"known missed bite returns its reservation");
        ok(await(stock.finish(other.id(),false)).state().equals("RELEASED"),"duplicate release is harmless");
        int remaining=await(stock.inspect(player,point,settings,now+20000)).remaining();stock.close();
        var restarted=new FishStock(directory.resolve("stock.db"));await(restarted.ready());
        ok(await(restarted.inspect(player,point,settings,now+20000)).remaining()==remaining,"restart preserves consumed and uncompleted stock debits");
        await(restarted.compact(Long.MAX_VALUE));ok(await(restarted.inspect(player,point,settings,now+20000)).remaining()==remaining,"even an oversized compaction cutoff cannot erase active fish debt");
        ok(await(restarted.inspect(player,point,settings,now-3600000)).remaining()==remaining,"clock rollback does not invent fish");
        ok(await(restarted.inspect(player,point,settings,now+180000)).remaining()==3,"full interval restores original capacity");
        ok(await(restarted.reserve(first.id(),first.player(),point,settings,now+180000)).state().equals("CAUGHT"),"old catch receipt remains protected after regeneration");
        restarted.close();
        Path blocked=directory.resolve("bad");Files.writeString(blocked,"not a directory");var failed=new FishStock(blocked.resolve("stock.db"));
        try{await(failed.ready());throw new AssertionError("unwritable store accepted");}catch(ExecutionException expected){checks++;}
        ok(!failed.available(),"unavailable durable store cannot authorize fishing");failed.close();
        var cfg=new org.bukkit.configuration.file.YamlConfiguration();var defaults=FishingSettings.read(cfg);
        ok(defaults.enabled()&&!defaults.withoutTask()&&defaults.requireOpenWater(),"defaults protect quests while keeping ordinary fishing unchanged");
        cfg.set("stock.radius",0);try{FishingSettings.read(cfg);throw new AssertionError("invalid radius accepted");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("FISH_STOCK_SMOKE_PASS: "+checks+" checks");
    }
}
