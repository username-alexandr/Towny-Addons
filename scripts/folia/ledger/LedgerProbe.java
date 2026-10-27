package ru.neverland.validation;

import com.palmergames.bukkit.towny.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.townychronicles.service.*;
import ru.neverland.townychronicles.integration.TownyHook;

/** Isolated test only: holds the shared ledger on a worker while real Folia ticks and retries. */
public final class LedgerProbe extends JavaPlugin {
    final List<String> checks=new CopyOnWriteArrayList<>();
    final AtomicInteger heartbeat=new AtomicInteger(),once=new AtomicInteger(),periodic=new AtomicInteger(),cancelled=new AtomicInteger();
    final CountDownLatch release=new CountDownLatch(1);final AtomicBoolean holding=new AtomicBoolean(),chunkLoaded=new AtomicBoolean();
    final UUID city=UUID.nameUUIDFromBytes("ledger-chronicles-fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    Plugin owner;Path proof;int before;boolean done;
    final String runId=System.getProperty("neverland.probe.run","legacy");
    org.bukkit.scheduler.BukkitTask timer;
    void check(boolean value,String name){if(!value)throw new AssertionError(name);checks.add("PASS "+name);}
    Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    @Override public void onEnable(){
        if(!Files.exists(Path.of("ALLOW_DISPOSABLE_LEDGER_PROBE"))||!getServer().getIp().equals("127.0.0.1")){getServer().getPluginManager().disablePlugin(this);return;}
        proof=getDataFolder().toPath();owner=Bukkit.getPluginManager().getPlugin("NeverLandTownyChronicles");
        if(!runId.equals("legacy"))proof=proof.resolve("runs").resolve(runId);
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this,t->heartbeat.incrementAndGet(),1,1);
        if(Boolean.getBoolean("neverland.probe.shutdown"))Bukkit.getGlobalRegionScheduler().runAtFixedRate(this,t->{
            Path other=Path.of("plugins/NeverLandValidationProbe");if(!runId.equals("legacy"))other=other.resolve("runs").resolve(runId);
            if(Files.exists(proof.resolve("result.txt"))&&Files.exists(other.resolve("result.txt"))&&(Bukkit.getPluginManager().getPlugin("NeverLandBuildMenuProbe")==null||Files.exists(Path.of("plugins/NeverLandBuildMenuProbe/runs/"+runId+"/result.txt")))){t.cancel();Bukkit.shutdown();}
        },20,20);
        Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{try{start();}catch(Throwable e){finish(e);}},80);
    }
    void start()throws Exception{
        check(owner!=null&&owner.isEnabled(),"Chronicles enabled with new shared core");
        check(SuiteState.protocolVersion()==2,"ledger protocol v2");
        Bukkit.getAsyncScheduler().runNow(this,t->{
            try(var guard=SuiteState.guard()){
                holding.set(true);
                try{if(!release.await(10,TimeUnit.SECONDS))checks.add("FAIL global scheduler did not release holder in time");}
                catch(InterruptedException e){Thread.currentThread().interrupt();checks.add("FAIL holder interrupted");}
                finally{holding.set(false);}
            }
        });
        waitHolder(0);
    }
    void waitHolder(int attempt){Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{try{
        if(!holding.get()){if(attempt>100)throw new AssertionError("holder timeout");waitHolder(attempt+1);return;}
        before=heartbeat.get();
        // Force the native chunk-load listener path to overlap the occupied ledger.
        Bukkit.getWorlds().get(0).getChunkAtAsync(128,128).whenComplete((chunk,error)->{
            if(error!=null){checks.add("FAIL chunk load: "+error);return;}
            Bukkit.getRegionScheduler().run(this,new org.bukkit.Location(chunk.getWorld(),2048,70,2048),x->{
                Bukkit.getPluginManager().callEvent(new org.bukkit.event.world.ChunkLoadEvent(chunk,false));
                Bukkit.getPluginManager().callEvent(new org.bukkit.event.world.ChunkPopulateEvent(chunk));
                chunkLoaded.set(true);
            });
        });
        SuiteScheduler.get().runTask(owner,()->{check(Bukkit.isGlobalTickThread()&&SuiteState.held(),"deferred callback returns to native global owner with ledger held");once.incrementAndGet();});
        timer=SuiteScheduler.get().runTaskTimer(owner,periodic::incrementAndGet,1,200);
        var cancelledTask=SuiteScheduler.get().runTask(owner,cancelled::incrementAndGet);cancelledTask.cancel();
        Bukkit.getGlobalRegionScheduler().runDelayed(this,x->{try{
            check(holding.get(),"worker still owns ledger after contention interval");
            check(heartbeat.get()-before>=100,"global heartbeat continues while ledger held for 120 ticks");
            check(chunkLoaded.get(),"region chunk-load and population handlers return while ledger is held");
            check(once.get()==0&&periodic.get()==0,"busy callbacks wait in their queues, not on global thread");
            check(SuiteState.deferredCount()>0,"contention counted");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"nltlock");
            checks.add("PASS nltlock completes while another thread owns ledger");
            release.countDown();
            Bukkit.getGlobalRegionScheduler().runDelayed(this,y->{try{afterRelease();}catch(Throwable e){finish(e);}},30);
        }catch(Throwable e){finish(e);}},120);
    }catch(Throwable e){finish(e);}},1);}
    void afterRelease(){
        check(!holding.get(),"worker releases ledger");check(once.get()==1&&periodic.get()==1,"deferred single and periodic tasks execute exactly once");
        check(cancelled.get()==0,"cancelled deferred task never executes");timer.cancel();
        SuiteScheduler.get().runTask(owner,()->{try{chronicles();}catch(Throwable e){finish(e);}});
    }
    void chronicles()throws Exception{
        check(!TownyEconomyHandler.isActive(),"fixture economy is deliberately unavailable");
        var tracker=(ChronicleTracker)field(owner,"tracker");var repository=(ChronicleRepository)field(owner,"repository");tracker.stop();
        var universe=TownyUniverse.getInstance();var town=universe.getTown(city);boolean restarted=town!=null;
        if(!restarted){
            universe.newTownInternal("LedgerTown",city);town=universe.getTown(city);
            var resident=universe.getDataSource().newResident("LedgerMayor",UUID.nameUUIDFromBytes("ledger-mayor".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            resident.setTown(town);town.setMayor(resident);resident.save();town.save();
            var state=repository.state(city);state.balance(4321);state.foundedAt(System.currentTimeMillis());state.name(town.getName());state.lastSeen(0);repository.markDirty();
        }else check(repository.state(city).balance()==4321,"previous balance survives full server restart without economy");
        check(new TownyHook().availableBalance(town).isEmpty(),"unavailable economy does not initialize Towny account");
        long seen=repository.state(city).lastSeen();int count=tracker.scanAll(true);
        check(count>=1&&repository.state(city).lastSeen()==seen,"rescan enqueues work instead of processing every city inline");
        SuiteScheduler.get().runTaskLater(owner,()->{try{
            var state=repository.state(city);check(state.lastSeen()>seen,"bounded scan processes real Towny city");
            check(state.balance()==4321,"missing provider does not replace cached treasury with zero");
            check(state.achievements().stream().noneMatch(x->x.startsWith("treasury-")),"unavailable balance cannot grant treasury achievement");
            repository.requestSave();
            Bukkit.getAsyncScheduler().runDelayed(this,t->{try{
                Path path=owner.getDataFolder().toPath().resolve("chronicles.yml");
                check(Files.exists(path)&&Files.readString(path).contains("4321"),"worker autosave durably stores preserved balance");
                SuiteScheduler.get().runTask(owner,()->{TownyUniverse.getInstance().getDataSource().saveAll();TownyUniverse.getInstance().getDataSource().saveQueues();finish(null);});
            }catch(Throwable e){finish(e);}},1,TimeUnit.SECONDS);
        }catch(Throwable e){finish(e);}},20);
    }
    synchronized void finish(Throwable error){
        if(done)return;done=true;release.countDown();if(timer!=null)timer.cancel();
        try{
            Files.createDirectories(proof);
            if(error!=null){checks.add("FAIL "+error);getLogger().log(java.util.logging.Level.SEVERE,"LEDGER_PROBE_FAIL",error);}
            Files.write(proof.resolve("checks.txt"),checks);
            Files.writeString(proof.resolve("result.txt"),"run_id="+runId+"\nfailed="+checks.stream().filter(x->x.startsWith("FAIL")).count()+"\nchecks="+checks.size()+"\n");
        }catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"Cannot save proof",e);}
    }
}
