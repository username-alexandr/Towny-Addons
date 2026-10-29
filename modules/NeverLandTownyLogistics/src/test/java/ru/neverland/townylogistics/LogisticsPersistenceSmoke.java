package ru.neverland.townylogistics;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;
import ru.neverland.townylogistics.data.LogisticsRepository;
import ru.neverland.townylogistics.model.*;
import ru.neverland.townylogistics.model.Network.*;

public final class LogisticsPersistenceSmoke {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("logistics-ack-"),file=dir.resolve("logistics-data.yml");UUID town=UUID.randomUUID(),world=UUID.randomUUID();
        var a=new Position(world,0,64,0);var b=new Position(world,8,64,0);var c=new Position(world,16,64,0);var route=new Route("wood","hub","source","target","",64,8,true);
        var network=new Network(town,Map.of("hub",new Node("hub","warehouse",Kind.BUILDING,a),"source",new Node("source","sawmill",Kind.BUILDING,b),"target",new Node("target","bakery",Kind.BUILDING,c)),Set.of(new Link("hub","source"),new Link("source","target")),Map.of("wood",route));
        var job=new CourierJob(UUID.randomUUID(),town,"wood","warehouse","sawmill","bakery",1,List.of(a,b),List.of(b,c),List.of(c,b,a),CourierJob.Phase.TO_TARGET,1,b,0,1,"Обход сохранён");
        var initial=new LogisticsRepository.Snapshot(Map.of(town,network),Map.of(job.id(),job));var repo=new LogisticsRepository(file);done(repo.loadAsync());done(repo.commit(repo.state(),initial));check(repo.state().equals(initial),"first state acknowledged");
        var field=AtomicFiles.class.getDeclaredField("writers");field.setAccessible(true);@SuppressWarnings("unchecked")var locks=(Map<Path,ReentrantLock>)field.get(null);var lock=locks.computeIfAbsent(file.toAbsolutePath().normalize(),key->new ReentrantLock());
        var next=new LogisticsRepository.Snapshot(initial.networks(),Map.of(job.id(),job.move(new Position(world,11,64,0),1)));CompletionStage<?> pending;CompletionStage<Void> closing;
        lock.lock();try{pending=repo.commit(initial,next);Thread.sleep(30);check(repo.busy()&&!pending.toCompletableFuture().isDone()&&repo.state().equals(initial),"delayed physical writer keeps old state visible");check(SuiteState.tryRun("logistics file wait",()->{}),"blocked writer does not hold suite guard");reject(()->done(repo.commit(initial,next)),"second mutation cannot race pending path");closing=repo.prepareClose();check(!closing.toCompletableFuture().isDone(),"shutdown waits for accepted pending path");}finally{lock.unlock();}
        done(pending);done(closing);check(repo.state().equals(next),"accepted update completes during shutdown");reject(()->done(repo.commit(next,initial)),"closed repository rejects new updates");
        var restarted=new LogisticsRepository(file);check(done(restarted.loadAsync()).equals(next),"full repository restart restores exact route, phase, waypoint and detour");done(restarted.prepareClose());
        var legacy=YamlConfiguration.loadConfiguration(file.toFile());legacy.set("schema",1);legacy.set("jobs."+job.id()+".detours",null);legacy.set("jobs."+job.id()+".recovery-note",null);String old=legacy.saveToString();Files.writeString(file,old);var migration=new LogisticsRepository(file);var migrated=done(migration.loadAsync());check(migrated.jobs().get(job.id()).detours()==0&&migrated.jobs().get(job.id()).position().equals(next.jobs().get(job.id()).position()),"schema one accepts legacy jobs without resetting movement");check(Files.readString(file).equals(old),"load migration never rewrites old data");done(migration.prepareClose());
        Files.writeString(file,"schema: 2\ntowns: [invalid\n");String damaged=Files.readString(file);var broken=new LogisticsRepository(file);reject(()->done(broken.loadAsync()),"malformed source refuses load");check(Files.readString(file).equals(damaged),"malformed source retained intact");done(broken.prepareClose());
        Path blocked=dir.resolve("blocked");var failing=new LogisticsRepository(blocked);done(failing.loadAsync());Files.createDirectory(blocked);Files.writeString(blocked.resolve("keep"),"original");reject(()->done(failing.commit(failing.state(),initial)),"actual filesystem replacement refusal");check(!failing.writable()&&failing.state().jobs().isEmpty()&&Files.readString(blocked.resolve("keep")).equals("original"),"failure closes mutation and never publishes cargo movement");done(failing.prepareClose());
        System.out.println("LogisticsPersistenceSmoke PASS "+checks+" checks");
    }
    private static <T>T done(CompletionStage<T> result)throws Exception{return result.toCompletableFuture().get(5,TimeUnit.SECONDS);}
    private interface Checked{void run()throws Exception;}
    private static void reject(Checked action,String reason)throws Exception{boolean failed=false;try{action.run();}catch(Exception expected){failed=true;}check(failed,reason);}
}
