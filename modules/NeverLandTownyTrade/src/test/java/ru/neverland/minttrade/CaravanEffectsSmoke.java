package ru.neverland.minttrade;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import ru.neverland.core.*;
import ru.neverland.minttrade.service.CaravanEffects;

public final class CaravanEffectsSmoke {
    static int checks;static void check(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
    static <T>T await(CompletionStage<T> f)throws Exception{return f.toCompletableFuture().get(5,TimeUnit.SECONDS);}
    static void rejects(CompletionStage<?> f,String why)throws Exception{try{await(f);}catch(ExecutionException e){check(true,why);return;}throw new AssertionError(why);}
    static ReentrantLock lock(Path path)throws Exception{var f=AtomicFiles.class.getDeclaredField("writers");f.setAccessible(true);@SuppressWarnings("unchecked")var locks=(java.util.concurrent.ConcurrentMap<Path,ReentrantLock>)f.get(null);return locks.computeIfAbsent(path.toAbsolutePath().normalize(),p->new ReentrantLock());}
    public static void main(String[] args)throws Exception{
        Path file=Files.createTempDirectory("caravan-ack-effects-").resolve("effects.yml");var store=new CaravanEffects(file);store.load();UUID id=UUID.randomUUID();var calls=new AtomicInteger();var gate=lock(file);gate.lock();CompletionStage<Boolean> payment;
        try{payment=store.execute(id,"seller 80",()->{calls.incrementAndGet();try{check(Files.readString(file).contains("PENDING"),"external bank only after durable pending");}catch(Exception e){throw new RuntimeException(e);}return CompletableFuture.completedFuture(true);});
            try(var guard=SuiteState.guard()){check(calls.get()==0&&!payment.toCompletableFuture().isDone(),"blocked actual writer neither calls bank nor holds SuiteState");check(store.state(id)==EffectJournal.State.READY,"unacknowledged pending not published");}
            rejects(store.execute(UUID.randomUUID(),"other",()->CompletableFuture.completedFuture(true)),"overlapping intent is rejected");
        }finally{gate.unlock();}
        check(await(payment)&&calls.get()==1&&store.state(id)==EffectJournal.State.DONE,"acknowledged payment completes once");
        check(await(store.execute(id,"seller 80",()->{calls.incrementAndGet();return CompletableFuture.completedFuture(true);}))&&calls.get()==1,"confirmed receipt cannot call bank twice");
        rejects(store.execute(id,"seller 81",()->CompletableFuture.completedFuture(true)),"same ID cannot change paid conditions");await(store.prepareClose());
        var restart=new CaravanEffects(file);restart.load();check(await(restart.execute(id,"seller 80",()->{calls.incrementAndGet();return CompletableFuture.completedFuture(true);}))&&calls.get()==1,"restart retains confirmed debit without replay");
        UUID unknown=UUID.randomUUID();rejects(restart.execute(unknown,"compensation 20",()->{calls.incrementAndGet();return CompletableFuture.failedFuture(new IllegalStateException("lost response after external mutation"));}),"unknown external reply leaves pending");
        check(restart.state(unknown)==EffectJournal.State.PENDING,"pending persists after unknown outcome");await(restart.prepareClose());
        var third=new CaravanEffects(file);third.load();rejects(third.execute(unknown,"compensation 20",()->{calls.incrementAndGet();return CompletableFuture.completedFuture(true);}),"restart cannot retry unknown bank call");check(calls.get()==2,"unknown call counted only once");
        await(third.resolve(unknown,true));check(await(third.execute(unknown,"compensation 20",()->{calls.incrementAndGet();return CompletableFuture.completedFuture(true);}))&&calls.get()==2,"manual applied result changes only journal");rejects(third.resolve(unknown,false),"resolved decision cannot be changed twice");
        UUID declined=UUID.randomUUID();check(!await(third.execute(declined,"refund 110",()->CompletableFuture.completedFuture(false)))&&third.state(declined)==EffectJournal.State.READY,"confirmed unchanged-bank rejection permits a later retry");
        UUID closing=UUID.randomUUID();var external=new CompletableFuture<Boolean>();var started=new CountDownLatch(1);var active=third.execute(closing,"await",()->{started.countDown();return external;});check(started.await(5,TimeUnit.SECONDS),"external stage started after its intent");check(third.state(closing)==EffectJournal.State.PENDING,"in-flight bank operation is pending");external.complete(true);await(active);await(third.prepareClose());
        var fourth=new CaravanEffects(file);fourth.load();check(fourth.state(closing)==EffectJournal.State.DONE,"completed external result saved before close");
        UUID legacy=UUID.randomUUID();await(fourth.review(Map.of(legacy,"old incomplete credit")));await(fourth.retain(Set.of(id)));check(fourth.state(legacy)==EffectJournal.State.PENDING,"pruning never discards unresolved legacy money");await(fourth.prepareClose());
        Path broken=Files.createTempDirectory("caravan-broken-effects-").resolve("effects.yml");var failed=new CaravanEffects(broken);failed.load();Files.createDirectory(broken);Files.writeString(broken.resolve("keep"),"preserved");var blockedCalls=new AtomicInteger();rejects(failed.execute(UUID.randomUUID(),"fail before bank",()->{blockedCalls.incrementAndGet();return CompletableFuture.completedFuture(true);}),"real atomic replace failure");check(blockedCalls.get()==0&&!failed.writable(),"failed intent never moves external money");await(failed.prepareClose());
        System.out.println("CaravanEffectsSmoke PASS "+checks+" checks");
    }
}
