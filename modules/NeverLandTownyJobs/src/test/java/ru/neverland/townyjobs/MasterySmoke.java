package ru.neverland.townyjobs;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.SuiteState;
import ru.neverland.townyjobs.mastery.*;
import static ru.neverland.townyjobs.mastery.MasteryModel.*;
/** Actual SQLite, immutable order terms, duplicate proofs, failure atomicity and restart. */
public final class MasterySmoke {
    static final UUID modelOrder=UUID.randomUUID();static MasteryStore store;static int checks;static UUID player=UUID.randomUUID(),town=UUID.randomUUID();
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    interface Work{void run()throws Exception;}
    static void reject(Work work,String why)throws Exception{try{work.run();}catch(Exception expected){checks++;return;}throw new AssertionError(why);}
    static <T>T done(CompletionStage<T> future)throws Exception{return future.toCompletableFuture().get(7,TimeUnit.SECONDS);}
    static void close(MasteryStore store)throws Exception{store.close();done(store.closed());}
    static Proof proof(Action action,String target,int amount){return new Proof(UUID.randomUUID(),player,town,store!=null&&store.get(player).active()!=null?store.get(player).active().id():modelOrder,Profession.BLACKSMITH,action,target,amount,System.currentTimeMillis());}
    static YamlConfiguration read(String resource)throws Exception{var y=new YamlConfiguration();try(var in=MasterySmoke.class.getResourceAsStream("/"+resource)){y.load(new java.io.InputStreamReader(Objects.requireNonNull(in),java.nio.charset.StandardCharsets.UTF_8));}return y;}
    static Definition definition(String id,double bonus){return new Definition(id,Profession.BLACKSMITH,"Кузнечный мастер","IRON_SWORD",2,bonus,Set.of("forge"),List.of(new Step(Action.SMELT,"IRON_INGOT",4,"Выплавьте четыре слитка"),new Step(Action.CRAFT,"IRON_SWORD",2,"Сделайте два меча")));}
    public static void main(String[] args)throws Exception{
        var cfg=read("config.yml");var allowed=new EnumMap<Profession,Set<String>>(Profession.class);for(Profession p:Profession.values())allowed.put(p,Set.copyOf(cfg.getStringList("profiles."+p.id()+".buildings")));var settings=MasterySettings.parse(read("mastery.yml"),allowed);
        check(settings.definitions().size()==14,"fourteen configured specialties across seven professions");for(Profession p:Profession.values())check(settings.definitions().values().stream().filter(d->d.profession()==p).count()==2,"two branches for "+p);
        var def=definition("toolsmith",.02);var state=Worker.empty().start(modelOrder,town,def,System.currentTimeMillis());check(state.action(proof(Action.CRAFT,"IRON_SWORD",2)).equals(state),"future step cannot be completed out of order");
        var next=state.action(proof(Action.SMELT,"IRON_INGOT",64));check(next.active().stage()==1&&next.active().progress()==0,"excess result cannot spill into the next recipe");
        check(next.action(new Proof(UUID.randomUUID(),player,town,UUID.randomUUID(),Profession.BLACKSMITH,Action.CRAFT,"IRON_SWORD",2,System.currentTimeMillis())).equals(next),"old order proof cannot advance a replacement order");
        check(next.action(new Proof(UUID.randomUUID(),player,UUID.randomUUID(),modelOrder,Profession.BLACKSMITH,Action.CRAFT,"IRON_SWORD",2,System.currentTimeMillis())).equals(next),"other city cannot complete bound order");
        check(next.action(new Proof(UUID.randomUUID(),player,town,modelOrder,Profession.ENGINEER,Action.CRAFT,"IRON_SWORD",2,System.currentTimeMillis())).equals(next),"other profession cannot complete the work");
        var complete=next.action(proof(Action.CRAFT,"IRON_SWORD",2));check(complete.active()==null&&complete.unlocked().get("toolsmith").bonus()==.02&&complete.bonus(Profession.BLACKSMITH,"forge")==0,"finished plan unlocks but does not silently choose a bonus");
        var chosen=complete.choose(Profession.BLACKSMITH,"toolsmith");check(chosen.bonus(Profession.BLACKSMITH,"forge")==.02&&chosen.bonus(Profession.BLACKSMITH,"foundry")==0,"chosen specialty applies only to matching profession and buildings");
        var folder=Files.createTempDirectory("mastery-store-");var file=folder.resolve("mastery.db");store=new MasteryStore(file);done(store.ready());UUID order=UUID.randomUUID();done(store.start(player,order,town,def,System.currentTimeMillis()));
        check(done(store.start(player,order,town,def,System.currentTimeMillis())).active().id().equals(order),"stable start id cannot create a second order");reject(()->done(store.start(player,order,town,definition("toolsmith",.03),System.currentTimeMillis())),"same id cannot change accepted reward terms");
        var first=proof(Action.SMELT,"IRON_INGOT",2);check(done(store.action(first))&&!done(store.action(first))&&store.get(player).active().progress()==2,"proof and partial progress commit exactly once");
        var alias=new Proof(first.id(),player,town,order,Profession.BLACKSMITH,Action.SMELT,"IRON_INGOT",3,first.at());reject(()->done(store.action(alias)),"same proof id cannot change resource quantity");check(store.healthy(),"invalid caller terms do not poison healthy storage");
        close(store);store=new MasteryStore(file);done(store.ready());check(store.get(player).active().progress()==2&&!done(store.action(first)),"restart preserves partial work and duplicate protection");
        var ignored=proof(Action.CRAFT,"IRON_SWORD",2);check(!done(store.action(ignored)),"early recipe proof is remembered but not applied");done(store.action(proof(Action.SMELT,"IRON_INGOT",2)));check(store.get(player).active().stage()==1&&!done(store.action(ignored)),"early ignored proof cannot be replayed into a later stage");
        done(store.action(proof(Action.CRAFT,"IRON_SWORD",2)));done(store.choose(player,Profession.BLACKSMITH,"toolsmith"));close(store);store=new MasteryStore(file);done(store.ready());check(store.get(player).bonus(Profession.BLACKSMITH,"forge")==.02,"chosen frozen reward survives restart");
        UUID second=UUID.randomUUID();done(store.start(player,second,town,definition("armorer",.03),System.currentTimeMillis()));done(store.cancel(player,second));check(store.get(player).active()==null&&store.get(player).bonus(Profession.BLACKSMITH,"forge")==.02,"neutral cancellation preserves previously earned specialty");
        reject(()->done(store.choose(player,Profession.FARMER,"toolsmith")),"cannot choose a specialty of another profession");
        done(store.start(player,UUID.randomUUID(),town,definition("armorer",.03),System.currentTimeMillis()));
        var stale=proof(Action.SMELT,"IRON_INGOT",4);var cancel=store.cancel(player,store.get(player).active().id());var replaced=store.start(player,UUID.randomUUID(),town,definition("armorer",.03),System.currentTimeMillis());var delayed=store.action(stale);done(cancel);done(replaced);check(!done(delayed)&&store.get(player).active().stage()==0,"queued old action cannot credit a newly accepted order after cancellation");
        var lockedStore=store;try(var external=DriverManager.getConnection("jdbc:sqlite:"+file)){external.setAutoCommit(false);try(var s=external.createStatement()){s.executeUpdate("INSERT INTO meta VALUES('native-test-lock','held')");}var blocked=store.action(proof(Action.SMELT,"IRON_INGOT",4));
            try(var guard=SuiteState.guard()){check(store.get(player).active().stage()==0,"unacknowledged work stays invisible without blocking the suite ledger");}
            reject(()->done(blocked),"actual SQLite write lock must fail the work atomically");external.rollback();
        }
        check(!lockedStore.healthy()&&lockedStore.get(player).active().stage()==0,"failed commit grants no progress or new production bonus");close(lockedStore);
        store=new MasteryStore(file);done(store.ready());check(store.get(player).active().stage()==0,"failed proof did not reappear after restart");close(store);Files.delete(file);
        var missing=new MasteryStore(file);reject(()->done(missing.ready()),"lost required database cannot silently reset earned specialties");close(missing);
        System.out.println("MasterySmoke PASS "+checks+" checks");
    }
}
