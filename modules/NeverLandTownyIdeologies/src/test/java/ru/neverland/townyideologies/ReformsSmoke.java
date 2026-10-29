package ru.neverland.townyideologies;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townyideologies.data.DataStore;
import ru.neverland.townyideologies.model.*;
import ru.neverland.townyideologies.reforms.ReformBook;
import static ru.neverland.townyideologies.reforms.ReformBook.*;

public final class ReformsSmoke {
    static int checks;static final UUID CITY=UUID.randomUUID(),ACTOR=UUID.randomUUID();
    static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
    interface Action{void run()throws Exception;}
    static void rejects(Action action,String message)throws Exception{try{action.run();}catch(Exception expected){checks++;return;}throw new AssertionError(message);}
    static PolicyTerms terms(String name,double growth){return new PolicyTerms(name,"TOWN","DEMOCRACY",Map.of("growth-multiplier",growth,"smelt-multiplier",1D,"health-hearts",0D,"minecart-multiplier",1D,"placement-cooldown-ticks",0D,"trade-output-multiplier",1D),List.of());}
    static TownIdeology old(){return new TownIdeology(CITY,"City","agriculture",1,123,terms("Old agriculture",1.25));}
    static Reform reform(long cents){return new Reform(UUID.randomUUID(),ACTOR,old(),new TownIdeology(CITY,"City","agriculture",2,123,terms("Accepted agriculture",1.5)),cents,List.of(1000L,2000L,1000L),0,Phase.RUNNING,false,100,0,List.of());}
    static State initial(){return new State(Map.of(CITY,old()),Map.of());}
    public static void main(String[] args)throws Exception{
        var r=reform(5000);var start=ReformBook.begin(initial(),r);
        check(start.towns().get(CITY).equals(old()),"begin never activates target benefits before payment");
        rejects(()->ReformBook.begin(start,reform(5000)),"parallel city reform rejected");
        rejects(()->ReformBook.preparePayment(start,r.id(),UUID.randomUUID(),ACTOR,101),"cannot pay before time stages complete");
        var stage1=ReformBook.advance(start,1000);check(stage1.active(CITY).orElseThrow().stage()==1,"preparation completes after exact worked time");
        var paused=ReformBook.pause(stage1,r.id(),true);check(ReformBook.advance(paused,10000).equals(paused),"pause consumes no time");
        var resumed=ReformBook.pause(paused,r.id(),false);var second=ReformBook.advance(resumed,1999);check(second.active(CITY).orElseThrow().stage()==1,"discussion one millisecond short remains incomplete");
        var adopting=ReformBook.advance(second,1);check(adopting.active(CITY).orElseThrow().stage()==2,"third stage follows real saved elapsed work");
        var ready=ReformBook.advance(adopting,1000);check(ready.active(CITY).orElseThrow().phase()==Phase.READY&&ready.towns().get(CITY).equals(old()),"ready requires separate payment, retains old benefit");
        check(ReformBook.advance(ready,10000).equals(ready),"ready state cannot auto-charge or auto-activate");
        rejects(()->ReformBook.advance(start,10001),"large tick cannot count downtime as work");
        rejects(()->ReformBook.advance(start,-1),"negative clock rejected");
        var cancelled=ReformBook.cancel(ready,r.id(),500);check(cancelled.active(CITY).isEmpty()&&cancelled.towns().equals(initial().towns()),"cancel is neutral with exact old effects and no financial receipt");
        var pending=ReformBook.preparePayment(ready,r.id(),UUID.randomUUID(),ACTOR,600);
        check(pending.active(CITY).orElseThrow().phase()==Phase.PAYMENT_PENDING&&pending.towns().equals(initial().towns()),"uncertain payment retains old active terms");
        rejects(()->ReformBook.cancel(pending,r.id(),601),"unknown payment cannot be blindly cancelled");
        rejects(()->ReformBook.preparePayment(pending,r.id(),UUID.randomUUID(),ACTOR,601),"unknown payment cannot be repeated");
        rejects(()->ReformBook.set(pending,CITY,null),"admin reset cannot erase an unknown receipt");
        var noPay=ReformBook.resolve(pending,r.id(),false,601);check(noPay.active(CITY).orElseThrow().phase()==Phase.READY&&noPay.towns().equals(initial().towns()),"known no-op restores exact ready reform");
        var retry=ReformBook.preparePayment(noPay,r.id(),UUID.randomUUID(),ACTOR,602);var paid=ReformBook.resolve(retry,r.id(),true,603);
        check(paid.active(CITY).isEmpty()&&paid.towns().get(CITY).equals(r.target()),"receipt and exact target activate atomically");
        check(paid.reforms().get(r.id()).attempts().stream().filter(a->a.result().equals("PAID")).count()==1,"one paid receipt with failed attempt retained");
        check(paid.towns().get(CITY).selectedAt()==123,"upgrade preserves prior selection timestamp");
        rejects(()->ReformBook.resolve(paid,r.id(),true,604),"completed payment cannot be replayed");
        var free=reform(0);var freeReady=ReformBook.advance(ReformBook.begin(initial(),free),4000);check(ReformBook.free(freeReady,free.id(),700).towns().get(CITY).equals(free.target()),"free reform still requires time and explicit completion");
        check(DataStore.decode(yaml(DataStore.encode(paid))).equals(paid),"entire ledger and immutable terms round-trip");
        var legacy=yaml("towns:\n  "+CITY+":\n    town-name: Legacy\n    ideology: agriculture\n    level: 4\n    selected-at: 222\n");var migrated=DataStore.decode(legacy);
        check(migrated.towns().get(CITY).level()==4&&migrated.towns().get(CITY).selectedAt()==222&&migrated.reforms().isEmpty(),"authentic old file remains already paid active without new reform");
        check(DataStore.decode(yaml(DataStore.encode(migrated))).equals(migrated),"legacy mode/effects snapshot absence survives migration unchanged");
        var bad=yaml(DataStore.encode(paid));bad.set("reforms."+r.id()+".cents",-1);rejects(()->DataStore.decode(bad),"corrupt price rejected");
        persistence(start,pending,paid);
        System.out.println("ReformsSmoke PASS: "+checks+" assertions");
    }
    static YamlConfiguration yaml(String text)throws Exception{var y=new YamlConfiguration();y.loadFromString(text);return y;}
    static void persistence(State start,State pending,State paid)throws Exception{
        Path dir=Files.createTempDirectory("ideology-reform-");Path file=dir.resolve("towns.yml");DataStore store=new DataStore(file);
        try{
            rejects(()->store.commit(ReformBook.empty(),start).toCompletableFuture().get(),"write before load rejected");store.load();store.commit(store.state(),start).toCompletableFuture().get(5,TimeUnit.SECONDS);check(store.state().equals(start),"acknowledged start is visible");
            var active=start.active(CITY).orElseThrow();var progressed=ReformBook.advance(start,1200);store.commit(start,progressed).toCompletableFuture().get(5,TimeUnit.SECONDS);store.awaitClosed();
            var restart=new DataStore(file);restart.load();check(restart.state().active(CITY).orElseThrow().worked()==1200,"restart cannot consume elapsed downtime");
            var ready=ReformBook.advance(restart.state(),2800);restart.commit(restart.state(),ready).toCompletableFuture().get(5,TimeUnit.SECONDS);var payment=ReformBook.preparePayment(ready,active.id(),UUID.randomUUID(),ACTOR,900);restart.commit(ready,payment).toCompletableFuture().get(5,TimeUnit.SECONDS);restart.awaitClosed();
            var unknown=new DataStore(file);unknown.load();check(unknown.state().equals(payment)&&unknown.state().towns().get(CITY).equals(old()),"restart with unknown bank result preserves PENDING, no effect/retry");
            var resolved=ReformBook.resolve(payment,active.id(),true,901);unknown.commit(payment,resolved).toCompletableFuture().get(5,TimeUnit.SECONDS);unknown.awaitClosed();
            var done=new DataStore(file);done.load();check(done.state().equals(resolved),"paid active target and receipt survive full storage reopen");byte[] before=Files.readAllBytes(file);done.commit(resolved,resolved).toCompletableFuture().get();check(Arrays.equals(before,Files.readAllBytes(file)),"unchanged state does not rewrite paid receipt");
            Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("keep"),"keep");var reset=ReformBook.set(resolved,CITY,null);rejects(()->done.commit(resolved,reset).toCompletableFuture().get(5,TimeUnit.SECONDS),"disk failure propagates");check(done.state().equals(resolved)&&!done.writable(),"failed write exposes no new active state");done.awaitClosed();Files.delete(file.resolve("keep"));Files.delete(file);Files.writeString(file,"towns: [broken");var corrupt=new DataStore(file);rejects(corrupt::load,"corrupt old file rejected");check(Files.readString(file).contains("[broken"),"corruption evidence not overwritten");corrupt.awaitClosed();
        }finally{store.awaitClosed();try(var paths=Files.walk(dir)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
    }
}
