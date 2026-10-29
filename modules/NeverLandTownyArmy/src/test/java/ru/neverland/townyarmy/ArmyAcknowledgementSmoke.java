package ru.neverland.townyarmy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import ru.neverland.core.*;
import static ru.neverland.townyarmy.ArmyModel.*;
import static ru.neverland.townyarmy.ArmyRepositorySmoke.done;
/** Hold the real AtomicFiles writer, not a completed-future stand-in. */
public final class ArmyAcknowledgementSmoke {
    static int checks;
    static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    interface Work{void run()throws Exception;}
    static void denied(Work w,String why)throws Exception{boolean refused=false;try{w.run();}catch(Exception expected){refused=true;}check(refused,why);}
    public static void main(String[] args)throws Exception{
        Path file=Files.createTempDirectory("army-ack-").resolve("army.yml");var store=new ArmyRepository(file,100);store.load();
        UUID town=UUID.randomUUID(),actor=UUID.randomUUID(),receipt=UUID.randomUUID();var cost=Map.of("food",1000L);var d=store.draft();d.imported=d.handoff=true;d.cities.put(town,City.empty(town).stock(Map.of("food",5000L)));done(store.commit(d));var before=store.state();var stale=store.draft();
        var f=AtomicFiles.class.getDeclaredField("writers");f.setAccessible(true);
        @SuppressWarnings("unchecked")var writers=(ConcurrentMap<Path,ReentrantLock>)f.get(null);var lock=writers.computeIfAbsent(file.toAbsolutePath().normalize(),p->new ReentrantLock());
        CompletionStage<Boolean> pending;CompletionStage<Void> close;
        lock.lock();try{
            pending=store.spendRaid(town,receipt,cost,1);
            try(var guard=SuiteState.guard()){
                check(store.busy()&&store.writable()&&!pending.toCompletableFuture().isDone(),"disk write leaves suite lock available, read health remains ready");
                check(store.state().equals(before)&&store.transfer(receipt)==null,"old stock and no raid receipt visible before fsync ACK");
                denied(()->done(store.spendRaid(town,receipt,cost,1)),"overlap and repeated click cannot add a second queued debit");
                close=store.close();check(!close.toCompletableFuture().isDone(),"stop drains accepted file write without blocking caller");
            }
        }finally{lock.unlock();}
        done(close);check(done(pending)&&store.state().cities().get(town).stock().get("food")==4000L,"accepted debit publishes once before shutdown finishes");
        denied(()->done(store.commit(stale)),"closed store rejects new changes");
        var restart=new ArmyRepository(file,100);restart.load();check(done(restart.spendRaid(town,receipt,cost,2)),"exact replay after process restart observes saved receipt");
        check(restart.state().equals(store.state()),"replay never mutates stock, audit or receipt time");
        denied(()->done(restart.spendRaid(town,receipt,Map.of("food",2000L),2)),"changed replay amount refused");
        var a=restart.draft();var b=restart.draft();a.cities.put(town,a.city(town).alert(true));done(restart.commit(a));b.cities.put(town,b.city(town).stock(Map.of("food",9000L)));denied(()->done(restart.commit(b)),"stale snapshot cannot restore spent resources");
        UUID supply=UUID.randomUUID();var plan=new Transfer(supply,town,actor,cost,Phase.PLANNED,3);var next=restart.draft();next.transfers.put(supply,plan);done(restart.commit(next));
        denied(()->done(restart.phase(plan.phase(Phase.APPLIED))),"credit cannot be bypassed by a phase-only update");
        done(restart.apply(supply));check(restart.state().cities().get(town).stock().get("food")==5000L&&restart.transfer(supply).phase()==Phase.APPLIED,"credit and applied phase share a durable image");
        done(restart.phase(plan.phase(Phase.CLOSED)));denied(()->done(restart.phase(plan)),"terminal transfer cannot return to planned");
        denied(()->done(restart.phase(new Transfer(UUID.randomUUID(),town,actor,cost,Phase.CLOSED,3))),"phase update cannot insert an unplanned receipt");
        done(restart.close());System.out.println("ArmyAcknowledgementSmoke PASS "+checks+" checks");
    }
}
