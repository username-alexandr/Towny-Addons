package ru.neverland.runtime;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import org.bukkit.*;
import org.bukkit.entity.Player;
import io.papermc.paper.threadedregions.scheduler.*;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.core.*;
import ru.neverland.morstownstick.MORSTownStick;
import ru.neverland.morstownstick.service.*;
import ru.neverland.morstownstick.model.CellKey;
import ru.neverland.morstownstick.plan.*;

/** Actual Towny claim batch, original Folia scheduler and durable plan repository. Offline actor is explicit. */
public final class StickExpansionProbe extends AdmissionsProbe {
    MORSTownStick stick;ClaimService claims;SelectionService selection;UUID successful,unknown;
    final AtomicBoolean permission=new AtomicBoolean(true),holdLaunch=new AtomicBoolean();final AtomicInteger locationReads=new AtomicInteger(),heldTasks=new AtomicInteger(),heartbeats=new AtomicInteger();
    interface Ready{boolean get()throws Exception;}
    void poll(int ticks,Ready condition,Work next)throws Exception{if(condition.get()){next.run();return;}if(ticks<=0)throw new AssertionError("Timeout: "+messages);later(1,()->poll(ticks-1,condition,next));}
    Player player(UUID id,String name){var base=actor(id,name);var original=Proxy.getInvocationHandler(base);var real=(EntityScheduler)originalValue(base,"getScheduler");
        var scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class<?>[]{EntityScheduler.class},(proxy,method,args)->{
            if(method.getName().equals("runDelayed")&&args[0]==stick&&holdLaunch.get()){
                heldTasks.incrementAndGet();return Proxy.newProxyInstance(ScheduledTask.class.getClassLoader(),new Class<?>[]{ScheduledTask.class},(p,m,a)->{if(m.getReturnType()==boolean.class)return false;if(m.getReturnType().isEnum())return m.getReturnType().getEnumConstants()[0];return null;});
            }try{return method.invoke(real,args);}catch(InvocationTargetException e){throw e.getCause();}
        });
        return(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()){
            case "getScheduler"->scheduler;case "getLocation"->{if(!Bukkit.isOwnedByCurrentRegion(origin))throw new AssertionError("Player location read outside entity region");locationReads.incrementAndGet();yield origin.clone();}
            case "hasPermission"->permission.get();case "getLocale"->"en_us";case "locale"->Locale.US;case "getEffectivePermissions"->Set.of();case "isPermissionSet"->true;case "isValid"->true;
            case "sendMessage","sendActionBar","sendPlayerListHeader","sendPlayerListFooter"->{messages.add(name+":"+Arrays.toString(args));yield null;}
            default->original.invoke(base,method,args);
        });
    }
    Object originalValue(Object value,String name){try{return value.getClass().getMethod(name).invoke(value);}catch(Exception e){throw new IllegalStateException(e);}}
    CellKey cell(int x){return new CellKey(world.getName(),x,0);}
    void townCommand(Player p,String... args){Bukkit.getPluginCommand("town").execute(p,"town",args);}
    ExpansionPlan current(UUID id){return claims.repository().state().plans().get(id);}
    boolean owned(int x){return new WorldCoord(world.getName(),x,0).getTownOrNull()==town;}
    @Override void setup()throws Exception{
        stick=(MORSTownStick)Bukkit.getPluginManager().getPlugin("NeverLandTownyStick");claims=stick.claims();selection=(SelectionService)field(stick,"selections");mayor=player(M,"ExpansionMayor");sponsor=player(S,"ExpansionAssistant");
        check(stick.isEnabled()&&stick.getDescription().getVersion().equals("0.2.0"),"actual Stick 0.2.0 enabled with native Towny and Folia");poll(80,()->claims.repository().ready(),()->{if(restart)resumeStick();else createCity();});
    }
    void createCity()throws Exception{
        var u=TownyUniverse.getInstance();u.newTownInternal("ExpansionCity",T);town=TownyAPI.getInstance().getTown(T);
        var m=u.getDataSource().newResident("ExpansionMayor",M);m.setTown(town);var s=u.getDataSource().newResident("ExpansionAssistant",S);s.setTown(town);s.addTownRank("assistant");town.setMayor(m);m.save();s.save();
        var home=new TownBlock(0,0,u.getWorld(world.getName()));u.addTownBlock(home);home.setTown(town);town.setHomeBlock(home);home.save();town.setSpawn(origin);town.save();
        selection.toggle(S,cell(2));selection.toggle(S,cell(1));townCommand(sponsor,"claim");
        poll(100,()->!claims.repository().busy()&&!claims.repository().state().plans().isEmpty(),this::created);
    }
    void created()throws Exception{
        var p=claims.repository().state().plans().values().iterator().next();successful=p.id();
        check(p.status()==ExpansionPlan.Status.DRAFT&&p.cells().equals(List.of(cell(1),cell(2)))&&!owned(1)&&!owned(2),"real selected /town claim creates saved BFS proposal without taking land");
        check(p.author().equals(S)&&p.mayor().equals(M)&&p.estimate()==0&&!p.economy(),"assistant creates mayor-bound estimate; fixture economy explicitly disabled");
        denied(()->claims.approve(sponsor,p.id(),1),"assistant cannot approve city expenditure");denied(()->claims.approve(mayor,p.id(),2),"stale revision cannot approve");
        permission.set(false);denied(()->claims.approve(mayor,p.id(),1),"revoked player permission rechecked");permission.set(true);
        town.setMayor(TownyAPI.getInstance().getResident(S));denied(()->claims.approve(sponsor,p.id(),1),"new mayor cannot approve predecessor estimate without revision");town.setMayor(TownyAPI.getInstance().getResident(M));
        var u=TownyUniverse.getInstance();var other=new TownBlock(0,1,u.getWorld(world.getName()));u.addTownBlock(other);other.setTown(town);other.save();
        denied(()->claims.approve(mayor,p.id(),1),"changed city area invalidates stored quote before approval");
        await(claims.revise(sponsor,p.id(),1),saved->{try{check(saved.revision()==2&&saved.status()==ExpansionPlan.Status.DRAFT,"revision refreshes exact quote and removes old approval");await(claims.approve(mayor,p.id(),2),approved->{try{blockedIntent();}catch(Exception e){fail(e);}});}catch(Exception e){fail(e);}});
    }
    void blockedIntent()throws Exception{
        check(current(successful).status()==ExpansionPlan.Status.APPROVED&&!owned(1),"approval ACK alone does not execute native claim");
        var type=Class.forName("ru.neverland.core.AtomicFiles",true,stick.getClass().getClassLoader());var field=type.getDeclaredField("writers");field.setAccessible(true);@SuppressWarnings("unchecked")var locks=(Map<Path,ReentrantLock>)field.get(null);var lock=locks.computeIfAbsent(stick.getDataFolder().toPath().resolve("expansion-plans.yml").toAbsolutePath(),p->new ReentrantLock());var entered=new CountDownLatch(1);release=new CountDownLatch(1);var thread=new Thread(()->{lock.lock();entered.countDown();try{release.await(12,TimeUnit.SECONDS);}catch(Exception ignored){}finally{lock.unlock();}},"native-expansion-blocked-file");thread.setDaemon(true);thread.start();if(!entered.await(1,TimeUnit.SECONDS))throw new AssertionError("lock unavailable");
        var heartbeat=SuiteScheduler.get().runTaskTimer(this,heartbeats::incrementAndGet,1,1);claims.execute(mayor,successful,2);
        later(4,()->{check(claims.repository().busy()&&current(successful).status()==ExpansionPlan.Status.APPROVED&&!owned(1)&&!owned(2),"blocked physical INTENT write cannot publish state or claim any land");check(heartbeats.get()>0,"real Suite global heartbeat advances while file writer is blocked");heartbeat.cancel();release.countDown();poll(160,()->!claims.repository().busy()&&current(successful).status()!=ExpansionPlan.Status.APPROVED&&current(successful).status()!=ExpansionPlan.Status.INTENT,this::completed);});
    }
    void completed()throws Exception{
        var p=current(successful);check(p.status()==ExpansionPlan.Status.OBSERVED&&p.observedOwned().size()==2&&owned(1)&&owned(2),"actual native TownClaim batch acquired both cells and independent ownership check confirms them");
        check(locationReads.get()>0,"TownClaim constructor read player location only on entity-owned region");denied(()->claims.execute(mayor,successful,2),"completed plan never repeats native claim or payment request");check(selection.count(S)==0,"successful cells removed from original assistant selection");
        selection.toggle(S,cell(3));await(claims.create(sponsor),draft->{try{unknown=draft.id();await(claims.approve(mayor,unknown,1),approved->{try{holdLaunch.set(true);await(claims.execute(mayor,unknown,1),intent->{try{poll(20,()->heldTasks.get()>0,()->{check(current(unknown).status()==ExpansionPlan.Status.INTENT&&!owned(3),"explicit held offline entity continuation leaves durable unknown INTENT before native effect");Files.writeString(proof.resolve("expected.txt"),successful+"\n"+unknown+"\n"+current(successful).updated()+"\n");finishStick();});}catch(Exception e){fail(e);}});}catch(Exception e){fail(e);}});}catch(Exception e){fail(e);}});
    }
    void resumeStick()throws Exception{
        town=TownyAPI.getInstance().getTown(T);var values=Files.readAllLines(proof.resolve("expected.txt"));successful=UUID.fromString(values.get(0));unknown=UUID.fromString(values.get(1));
        check(owned(1)&&owned(2)&&!owned(3),"real Towny ownership and unapplied unknown cell survive full restart");
        check(current(successful).status()==ExpansionPlan.Status.OBSERVED&&current(successful).updated()==Long.parseLong(values.get(2)),"acknowledged history restores exact original timestamp");
        check(current(unknown).status()==ExpansionPlan.Status.INTENT,"unknown INTENT restored without automatic continuation");denied(()->claims.execute(mayor,unknown,1),"unknown intent cannot be executed again");
        claims.show(mayor,unknown);check(messages.stream().anyMatch(m->m.contains("Нужна сверка администратора")),"restored unknown plan clearly explains manual review to player");
        stick.getCommand("townystick").execute(mayor,"townystick",new String[]{"review",unknown.toString(),"Участок не присоединён; экономика fixture выключена"});poll(100,()->!claims.repository().busy()&&current(unknown).status()==ExpansionPlan.Status.RECONCILED,()->{
            check(!owned(3)&&current(unknown).observedOwned().isEmpty(),"actual admin review records observation without claiming or paying again");
            long receipts;try(var paths=Files.walk(stick.getDataFolder().toPath().resolve("audit"))){receipts=paths.filter(p->p.toString().endsWith(".tsv")).mapToLong(p->{try{return Files.readString(p).lines().filter(l->l.contains("CITY_EXPANSION")).count();}catch(Exception e){throw new RuntimeException(e);}}).sum();}
            check(receipts==9,"durable administrative audit retains exact nine proposal/approval/intent/result/review transitions");finishStick();
        });
    }
    void finishStick()throws Exception{TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; actual Folia/Towny claim batch, saved mayor approval and revision, blocked fsync with Suite heartbeat, ownership check, restart no-replay and administrative review. Explicit offline Player/EntityScheduler; second launch deliberately held to model crash boundary; economy disabled, no monetary payment certified.\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
