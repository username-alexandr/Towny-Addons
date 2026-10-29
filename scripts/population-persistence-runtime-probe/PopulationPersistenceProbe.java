package ru.neverland.runtime;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import org.bukkit.*;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.core.*;
import ru.neverland.townypopulation.*;
import ru.neverland.townypopulation.service.*;
import ru.neverland.townypopulation.data.*;
import ru.neverland.townypopulation.model.*;

/** Real population writer and native schedulers; inherited offline actor is only used for menus. */
public final class PopulationPersistenceProbe extends AdmissionsProbe {
    NeverLandTownyPopulation populationPlugin;PopulationService population;PopulationRepository repository;Path data;byte[] confirmed;
    void in(Work work){try{work.run();}catch(Throwable error){fail(error);}}
    void ready(int remaining,Work next)throws Exception{if(repository.busy()||!Boolean.TRUE.equals(field(population,"startupReady"))){if(remaining<=0)throw new AssertionError("population startup not acknowledged");later(1,()->ready(remaining-1,next));return;}next.run();}
    @Override void setup()throws Exception{
        populationPlugin=(NeverLandTownyPopulation)Bukkit.getPluginManager().getPlugin("NeverLandTownyPopulation");population=(PopulationService)field(populationPlugin,"service");repository=(PopulationRepository)field(population,"repository");data=populationPlugin.getDataFolder().toPath().resolve("population-data.yml");
        check(populationPlugin.isEnabled()&&populationPlugin.getDescription().getVersion().equals("0.2.1"),"validated Population 0.2.1 enabled with actual Towny Builds Districts on Folia");
        ready(80,()->{if(restart){resumePopulation();return;}var u=TownyUniverse.getInstance();u.newTownInternal("PopulationCity",T);town=TownyAPI.getInstance().getTown(T);var r=u.getDataSource().newResident("PopulationMayor",M);r.setTown(town);town.setMayor(r);r.save();var claim=new TownBlock(0,0,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(town);town.setHomeBlock(claim);claim.save();town.setSpawn(origin);town.save();
            await(population.setPopulation(T,123),saved->in(()->{population.refreshTownView(T);check(saved&&population.population(T).orElseThrow().population()==123,"administrative population becomes visible only after acknowledged save");check(population.residentPopulation(M).orElseThrow().population()==123,"resident lookup uses same confirmed immutable city snapshot");delayWrite();}));});
    }
    @SuppressWarnings("unchecked")void delayWrite()throws Exception{
        var atomic=Class.forName("ru.neverland.core.AtomicFiles",true,populationPlugin.getClass().getClassLoader());var f=atomic.getDeclaredField("writers");f.setAccessible(true);var locks=(Map<Path,ReentrantLock>)f.get(null);var lock=locks.computeIfAbsent(data.toAbsolutePath().normalize(),k->new ReentrantLock());release=new CountDownLatch(1);var locked=new CountDownLatch(1);var thread=new Thread(()->{lock.lock();locked.countDown();try{release.await(15,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}finally{lock.unlock();}},"native-population-file-delay");thread.setDaemon(true);thread.start();if(!locked.await(1,TimeUnit.SECONDS))throw new AssertionError("cannot lock population file");
        var pending=population.setPopulation(T,124);later(3,()->{population.refreshTownView(T);check(repository.busy()&&!pending.toCompletableFuture().isDone()&&population.population(T).orElseThrow().population()==123,"physically delayed population file never exposes unconfirmed residents");check(SuiteState.tryRun("population native file delay",()->{}),"region suite state remains available while population disk writer waits");release.countDown();await(pending,saved->in(()->{population.refreshTownView(T);check(saved&&population.population(T).orElseThrow().population()==124,"confirmed worker completion publishes the whole new population");pauseDependency();}));});
    }
    void pauseDependency()throws Exception{
        var before=repository.state();var next=new HashMap<>(before);next.put(T,new PopulationState(124,.35,-2,System.currentTimeMillis()-population.settings().intervalMillis()-100));
        await(repository.commit(before,next),saved->in(()->{population.refreshTownView(T);var capacity=population.population(T).orElseThrow().capacity();SuiteState.freeze("NeverLandTownyDistricts");var pulse=population.getClass().getDeclaredMethod("pulse");pulse.setAccessible(true);pulse.invoke(population);ready(80,()->{population.refreshTownView(T);check(population.population(T).orElseThrow().paused()&&population.population(T).orElseThrow().population()==124&&population.population(T).orElseThrow().capacity().equals(capacity),"disabled real Districts supplier pauses a due population cycle without artificial decline or lost housing");check(repository.get(T).population()==124&&repository.get(T).remainder()==.35&&repository.get(T).lastChange()==-2&&repository.get(T).lastCycle()>next.get(T).lastCycle(),"paused cycle rebases time while preserving people fraction and decline history");SuiteState.thaw("NeverLandTownyDistricts");population.refreshTownView(T);check(!population.population(T).orElseThrow().paused(),"supplier recovery resumes confirmed population view");failWrite();});}));
    }
    void failWrite()throws Exception{
        confirmed=Files.readAllBytes(data);Files.delete(data);Files.createDirectory(data);Files.writeString(data.resolve("keep"),"do not replace");var pending=population.setPopulation(T,200);pending.whenComplete((value,error)->later(1,()->{
            check(error!=null&&repository.readable()&&!repository.writable()&&repository.get(T).population()==124,"actual atomic replacement failure retains confirmed residents and closes further writes");population.refreshTownView(T);check(population.healthy()&&population.population(T).orElseThrow().paused()&&population.population(T).orElseThrow().population()==124,"API keeps confirmed residents readable with explicit pause after disk failure");Files.delete(data.resolve("keep"));Files.delete(data);Files.write(data,confirmed);check(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(data.toFile()).getInt("schema")==1,"restored confirmed file retains original schema1");finishPopulation();}));
    }
    void resumePopulation()throws Exception{town=TownyAPI.getInstance().getTown(T);population.refreshTownView(T);check(town!=null&&repository.writable()&&population.population(T).orElseThrow().population()==124,"real full restart reloads last confirmed population and reopens repaired storage");check(repository.get(T).remainder()==.35&&repository.get(T).lastChange()==-2,"restart preserves fractional progress and change history");check(!population.population(T).orElseThrow().paused()&&repository.get(T).lastCycle()>System.currentTimeMillis()-60_000,"startup acknowledgement rebases elapsed downtime before any growth or decline");finishPopulation();}
    void finishPopulation()throws Exception{TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; real Folia Towny Builds Districts Population, asynchronous acknowledged population setter, physical file lock and replacement failure, supplier pause, immutable reads and full restart; no graphical client or bank transaction claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
