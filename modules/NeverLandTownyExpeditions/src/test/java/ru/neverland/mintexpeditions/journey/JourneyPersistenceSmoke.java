package ru.neverland.mintexpeditions.journey;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import ru.neverland.core.*;
public final class JourneyPersistenceSmoke {
    static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}static <T>T wait(CompletionStage<T> s)throws Exception{return s.toCompletableFuture().get(5,TimeUnit.SECONDS);}
    static void deny(CompletionStage<?> s)throws Exception{try{wait(s);}catch(Exception expected){checks++;return;}throw new AssertionError("expected failure");}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("expedition-journey-"),file=dir.resolve("journeys.yml");Map<String,AuditRecord> audit=new ConcurrentHashMap<>();var appendCount=new AtomicInteger();var fail=new AtomicBoolean();
        java.util.function.Predicate<AuditRecord> sink=r->{if(SuiteState.held())throw new AssertionError("audit holds Suite");if(audit.putIfAbsent(r.id(),r)==null)appendCount.incrementAndGet();return !fail.get();};
        var repo=new JourneyRepository(file,sink);wait(repo.load());UUID player=UUID.randomUUID();var journey=new Journey(UUID.randomUUID(),JourneySmoke.definition(),Journey.Branch.SCOUT_FIRST,Set.of(player),Map.of(),Map.of(),Journey.State.ACTIVE,"");
        var writers=AtomicFiles.class.getDeclaredField("writers");writers.setAccessible(true);@SuppressWarnings("unchecked")var locks=(Map<Path,ReentrantLock>)writers.get(null);var lock=locks.computeIfAbsent(file.toAbsolutePath(),p->new ReentrantLock());var acquired=new CountDownLatch(1);var release=new CountDownLatch(1);
        var holder=new Thread(()->{lock.lock();acquired.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(Exception e){throw new RuntimeException(e);}finally{lock.unlock();}});holder.start();check(acquired.await(1,TimeUnit.SECONDS),"writer physically blocked");var pending=repo.commit(null,journey,player,"created","Создан");check(repo.busy()&&repo.get(journey.id())==null&&!pending.toCompletableFuture().isDone(),"blocked write publishes no journey");var heartbeat=CompletableFuture.runAsync(()->{try(var guard=SuiteState.guard()){checks++;}});wait(heartbeat);check(appendCount.get()==0,"no audit before state ACK");release.countDown();wait(pending);holder.join();check(!repo.busy()&&repo.get(journey.id()).equals(journey)&&appendCount.get()==1,"state and audit ACK complete operation");
        fail.set(true);var updated=journey.credit("o:1,2,3",Journey.Action.OBJECTIVE,player,10);deny(repo.commit(journey,updated,player,"objective:1","Подтверждена цель"));check(!repo.ready()&&JourneyRepository.read(file).outbox().size()==1,"failed external audit retains exact durable receipt");var exact=JourneyRepository.read(file).outbox().values().iterator().next();wait(repo.prepareClose());
        fail.set(false);var restored=new JourneyRepository(file,sink);wait(restored.load());check(restored.get(journey.id()).objectives()==1&&restored.get(journey.id()).frozen().moneyPerParticipant()==40,"restart retains frozen conditions and contribution");check(appendCount.get()==2&&audit.get(exact.id()).equals(exact)&&JourneyRepository.read(file).outbox().isEmpty(),"replay keeps ID/time and emits no duplicate");check(restored.get(journey.id()).stage()==Journey.Action.OBJECTIVE,"restart never skips uncompleted stage");wait(restored.prepareClose());
        Files.writeString(file,"schema: 999\n");byte[] original=Files.readAllBytes(file);var broken=new JourneyRepository(file,sink);deny(broken.load());check(!broken.ready()&&Arrays.equals(original,Files.readAllBytes(file)),"corrupt optional journal retained unchanged");wait(broken.prepareClose());
        System.out.println("JourneyPersistenceSmoke PASS "+checks);
    }
}
