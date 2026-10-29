package ru.neverland.mintevents.scenarios;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.mintevents.model.ActiveEvent;
import static ru.neverland.mintevents.scenarios.ScenarioBook.*;

public final class ScenariosSmoke {
    static int checks;static final UUID T=UUID.randomUUID(),M=UUID.randomUUID();
    interface Work{void run()throws Exception;}
    static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    static void rejects(Work work,String why)throws Exception{try{work.run();}catch(Exception expected){checks++;return;}throw new AssertionError(why);}
    static Terms terms(){return new Terms("DROUGHT","Помощь при засухе",2000,70,List.of(new Plan("crops","Беречь посевы","Поливать поля",new Mix(.6,1.2),new Mix(.8,1.1)),new Plan("production","Сохранить выпуск","Поддержать производство",new Mix(1.2,.6),new Mix(1.1,.8))));}
    static Scenario fresh(){return ScenarioBook.fresh(UUID.randomUUID(),T,"Город","drought",100,terms());}
    static State advance(State state,Scenario r,long elapsed,SourceState source,int progress){return ScenarioBook.advance(state,elapsed,Map.of(r.id(),new Source(source,progress,100)),200);}
    public static void main(String[] args)throws Exception{
        var r=fresh();var start=begin(empty(),r);check(r.effects().equals(Mix.neutral()),"warning adds no undocumented effects");
        rejects(()->choose(start,r.id(),"crops",M,"Mayor",101),"warning cannot be skipped by a stale command");
        check(advance(start,r,10000,SourceState.PAUSED,0).equals(start),"paused source consumes no worked time");
        check(ScenarioBook.advance(start,1000,Map.of(),200).equals(start),"unavailable observation cannot fabricate a result");
        var almost=advance(start,r,1999,SourceState.RUNNING,0);check(require(almost,r.id()).phase()==Phase.WARNING,"one millisecond short remains warning");
        var decision=advance(almost,r,1,SourceState.RUNNING,0);check(require(decision,r.id()).phase()==Phase.DECISION&&require(decision,r.id()).effects().equals(Mix.neutral()),"decision requires explicit choice and adds no effects");
        rejects(()->choose(decision,r.id(),"missing",M,"Mayor",201),"unknown choice cannot be accepted");
        var selected=choose(decision,r.id(),"crops",M,"Mayor",201);var chosen=require(selected,r.id());check(chosen.effects().equals(new Mix(.6,1.2))&&chosen.actor().equals(M),"choice retains benefit, drawback and exact actor");
        rejects(()->choose(selected,r.id(),"production",M,"Mayor",202),"repeat choice cannot replace accepted terms");
        var edited=ScenarioBook.fresh(r.id(),T,"Changed name","drought",100,new Terms("DROUGHT","New title",1000,99,List.of(new Plan("one","One","One",Mix.neutral(),Mix.neutral()),new Plan("two","Two","Two",Mix.neutral(),Mix.neutral()))));
        check(begin(selected,edited).equals(selected),"reload or ordinary timer restart cannot reprice or replace accepted terms");
        check(advance(selected,r,10000,SourceState.PAUSED,80).equals(selected),"pause keeps accepted choice and progress threshold unchanged");
        check(require(advance(selected,r,0,SourceState.RUNNING,69),r.id()).phase()==Phase.RESPONSE,"recovery waits for actual main event progress");
        var recovery=advance(selected,r,0,SourceState.RUNNING,70);check(require(recovery,r.id()).effects().equals(new Mix(.8,1.1)),"70 percent progress weakens tradeoff using saved recovery terms");
        var complete=advance(recovery,r,0,SourceState.SUCCESS,100);check(require(complete,r.id()).phase()==Phase.COMPLETED&&require(complete,r.id()).outcome().equals("SUCCESS")&&require(complete,r.id()).effects().equals(Mix.neutral()),"real main success closes optional effects once");
        check(advance(complete,r,10000,SourceState.FAILURE,0).equals(complete),"terminal result cannot be replayed or changed");
        check(require(advance(selected,r,0,SourceState.FAILURE,30),r.id()).outcome().equals("FAILURE"),"main failure remains the sole failure authority");
        var missing=advance(selected,r,0,SourceState.MISSING,0);check(require(missing,r.id()).phase()==Phase.CANCELLED&&require(missing,r.id()).outcome().equals("UNAVAILABLE"),"missing or admin cancelled event creates no failure");
        check(require(cancel(selected,r.id(),300),r.id()).outcome().equals("CANCELLED"),"explicit cancellation is neutral");
        var active=new ActiveEvent(T,"drought",100,200,42,100,.2,0);check(active.reschedule(999,77).instanceId().equals(active.instanceId()),"timer and pause edits retain stable actual event instance");
        check(!new ActiveEvent(T,"drought",100,200,0,100,0,0).instanceId().equals(active.instanceId()),"same millisecond new event cannot inherit cancelled choice");
        check(new ru.neverland.mintevents.model.HistoryEntry("drought",100,200,1,10,false).instanceId()==null,"legacy history constructor remains compatible");
        check(ScenarioCodec.decode(yaml(ScenarioCodec.encode(recovery))).equals(recovery),"all phases, frozen terms and identity round trip");
        var bad=yaml(ScenarioCodec.encode(selected));bad.set("scenarios."+r.id()+".choice","unknown");rejects(()->ScenarioCodec.decode(bad),"corrupt choice cannot load");
        rejects(()->new Mix(Double.NaN,1),"nonfinite configured consequence rejected");rejects(()->advance(start,r,10001,SourceState.RUNNING,0),"long clock gaps cannot count as offline work");
        check(Math.abs(loss(.7,.6)-.42)<1e-9&&Math.abs(loss(.5,1.2)-.6)<1e-9,"crop rescue trades 42 percent obstruction for 60 percent output loss");
        check(loss(1,1.5)==.9&&loss(0,1.5)==0,"consequences bounded and fully protected city remains protected");
        var full=new HashMap<UUID,Scenario>();full.putAll(selected.entries());for(int i=1;i<MAX_HISTORY;i++){var f=fresh();full.put(f.id(),require(cancel(begin(empty(),f),f.id(),301),f.id()));}var atCap=new State(full);
        rejects(()->choose(new State(new HashMap<>(atCap.entries()){{put(r.id(),require(decision,r.id()));}}),r.id(),"crops",M,"Mayor",302),"full journal closes new choices");
        check(require(atCap,r.id()).effects().equals(new Mix(.6,1.2)),"full journal retains both sides of acknowledged choice");
        var resource=ScenariosSmoke.class.getClassLoader().getResourceAsStream("scenarios.yml");if(resource==null)throw new AssertionError("missing bundled catalog");Path catalog=Files.createTempFile("scenario-catalog-",".yml");Files.copy(resource,catalog,StandardCopyOption.REPLACE_EXISTING);var defaults=ScenarioCatalog.load(catalog);check(defaults.enabled()&&defaults.definitions().size()==2&&defaults.definitions().get("FLOOD").plans().size()==2,"bundled defaults include readable drought and flood choices");Files.delete(catalog);
        persistence(decision,selected);System.out.println("ScenariosSmoke PASS: "+checks+" assertions");
    }
    static YamlConfiguration yaml(String text)throws Exception{var y=new YamlConfiguration();y.loadFromString(text);return y;}
    static void close(ScenarioRepository r)throws Exception{r.prepareClose().toCompletableFuture().get(5,TimeUnit.SECONDS);}
    static void persistence(State decision,State selected)throws Exception{
        Path dir=Files.createTempDirectory("event-scenario-");Path path=dir.resolve("scenarios.yml");var repo=new ScenarioRepository(path);
        try{
            rejects(()->repo.commit(empty(),decision).toCompletableFuture().get(),"write before validated load rejected");repo.load();repo.commit(empty(),decision).toCompletableFuture().get(5,TimeUnit.SECONDS);
            var field=ru.neverland.core.AtomicFiles.class.getDeclaredField("writers");field.setAccessible(true);@SuppressWarnings("unchecked")var locks=(Map<Path,java.util.concurrent.locks.ReentrantLock>)field.get(null);var lock=locks.computeIfAbsent(path.toAbsolutePath().normalize(),p->new java.util.concurrent.locks.ReentrantLock());
            lock.lock();CompletionStage<State> pending;try{pending=repo.commit(decision,selected);check(repo.busy()&&repo.state().equals(decision),"physical delayed file cannot publish pending benefits or drawbacks");rejects(()->repo.commit(decision,selected).toCompletableFuture().get(),"second click cannot enqueue duplicate decision");check(ru.neverland.core.SuiteState.tryRun("scenario smoke",()->{}),"file delay does not hold global suite monitor");}finally{lock.unlock();}
            pending.toCompletableFuture().get(5,TimeUnit.SECONDS);check(repo.state().equals(selected),"ACK publishes full chosen snapshot");close(repo);
            var restored=new ScenarioRepository(path);restored.load();check(restored.state().equals(selected),"restart preserves exact terms and chosen timestamp without offline advancement");
            Files.delete(path);Files.createDirectory(path);Files.writeString(path.resolve("keep"),"keep");var next=cancel(selected,selected.entries().keySet().iterator().next(),500);rejects(()->restored.commit(selected,next).toCompletableFuture().get(5,TimeUnit.SECONDS),"physical write failure fails closed");check(restored.state().equals(selected)&&!restored.writable(),"failed write retains both accepted effects and actor");close(restored);
            Files.delete(path.resolve("keep"));Files.delete(path);Files.writeString(path,"scenarios: [broken");var corrupt=new ScenarioRepository(path);rejects(corrupt::load,"corrupt optional journal rejected without replacement");check(!corrupt.ready()&&Files.readString(path).contains("[broken"),"corrupt file preserved for admin diagnosis");close(corrupt);
            Files.writeString(path,"");var blank=new ScenarioRepository(path);rejects(blank::load,"existing empty journal cannot reset choices");close(blank);
        }finally{close(repo);try(var paths=Files.walk(dir)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    }
}
