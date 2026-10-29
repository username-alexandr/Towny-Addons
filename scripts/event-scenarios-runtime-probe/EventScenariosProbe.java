package ru.neverland.runtime;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.lang.reflect.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.core.*;
import ru.neverland.mintevents.MintTownyEvents;
import ru.neverland.mintevents.service.EventService;
import ru.neverland.mintevents.scenarios.*;
import static ru.neverland.mintevents.scenarios.ScenarioBook.*;

/** Actual Folia/Towny/event sources and native inventories. Only actors, scheduler and elapsed samples are substitutes. */
public final class EventScenariosProbe extends AdmissionsProbe {
    MintTownyEvents plugin;EventService events;ScenarioService scenarios;ScenarioRepository data;ScenarioMenus menus;UUID first,second;String phase;boolean permission=true;
    static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    void invoke(Object target,String method)throws Exception{var m=target.getClass().getDeclaredMethod(method);m.setAccessible(true);m.invoke(target);}
    void in(Work work){try{work.run();}catch(Throwable error){fail(error);}}
    @Override void check(boolean yes,String why)throws Exception{if(!yes)throw new AssertionError(why);checks++;Files.writeString(proof.resolve(phase+"-checks.txt"),"PASS "+why+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);getLogger().info("CHECK "+why);}
    @Override void setup()throws Exception{
        phase=Files.exists(proof.resolve("restart-passed.txt"))?"corrupt":restart?"restart":"first";
        plugin=(MintTownyEvents)Bukkit.getPluginManager().getPlugin("NeverLandTownyEvents");events=(EventService)field(plugin,"events");scenarios=events.scenarios();data=(ScenarioRepository)field(scenarios,"repository");invoke(events,"stopTasks");((org.bukkit.scheduler.BukkitTask)field(scenarios,"task")).cancel();
        Player base=actor(M,"ScenarioMayor");mayor=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->m.getName().equals("hasPermission")&&a[0].equals("mintevents.plan")?permission:m.invoke(base,a));menus=new ScenarioMenus(plugin,scenarios,p->{});
        check(plugin.isEnabled()&&plugin.getDescription().getVersion().equals("0.5.0"),"actual Events0.5 plugin enabled on real Folia");
        if(phase.equals("corrupt")){corrupt();return;}idle(()->{check(scenarios.ready()&&scenarios.problem().isBlank(),"optional journal and scenario catalog loaded asynchronously");if(restart)resumed();else first();});
    }
    void idle(Work next){idle(next,150);}
    void idle(Work next,int left){later(1,()->{if(data.busy()||(!scenarios.ready()&&left>0)){if(left<1)throw new AssertionError("scenario ACK timeout");idle(next,left-1);}else next.run();});}
    void pulse(long millis,Work next)throws Exception{set(scenarios,"pulseAt",System.nanoTime()-millis*1_000_000L);invoke(scenarios,"pulse");idle(next);}
    Scenario row(UUID id){return scenarios.find(id).orElseThrow();}
    void start(String name)throws Exception{check(events.startEvent(town,events.registry().get(name),true),"main event starts: "+name);}
    void first()throws Exception{
        var u=TownyUniverse.getInstance();u.newTownInternal("ScenarioCity",T);town=TownyAPI.getInstance().getTown(T);var resident=u.getDataSource().newResident("ScenarioMayor",M);resident.setTown(town);town.setMayor(resident);resident.save();var claim=new TownBlock(0,0,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(town);town.setHomeBlock(claim);claim.save();town.setSpawn(origin);town.save();
        start("drought");first=events.active(T).instanceId();check(scenarios.find(first).isEmpty()&&Math.abs(events.productionMultiplier(T,"agrarian_complex")-.5)<1e-9,"ordinary drought uses previous production loss before optional ACK");pulse(0,()->{check(row(first).phase()==Phase.WARNING,"new actual event gains saved warning phase");events.pause(town,true);pulse(10000,()->{check(row(first).worked()==0&&events.cropCancelChance(T)==0&&events.productionMultiplier(T,"agrarian_complex")==1,"admin pause stops optional clock and ordinary harmful effects");events.pause(town,false);pulse(1100,this::decision);});});
    }
    void decision()throws Exception{
        check(row(first).phase()==Phase.DECISION&&scenarios.canChoose(mayor,row(first)),"mayor can choose only after saved warning");permission=false;denied(()->scenarios.choose(mayor,first,"crops"),"actual permission loss rejects direct choice");permission=true;
        menus.open(mayor);check(lore(mayor,11).contains("40%")&&lore(mayor,11).contains("20%")&&lore(mayor,4).contains("не списывает"),"native plan inventory shows benefit drawback and no hidden contribution");
        holdJournal();var click=new InventoryClickEvent(view(mayor,M),InventoryType.SlotType.CONTAINER,11,ClickType.LEFT,InventoryAction.PICKUP_ALL);menus.click(click);check(click.isCancelled(),"native plan menu cancels inventory movement");later(3,()->{check(data.busy()&&row(first).phase()==Phase.DECISION,"physically blocked file never publishes clicked plan early");check(Math.abs(events.cropCancelChance(T)-.7)<1e-9&&Math.abs(events.productionMultiplier(T,"agrarian_complex")-.5)<1e-9,"pending plan changes neither crop obstruction nor output loss");check(SuiteState.tryRun("event live region while writer delayed",()->{}),"actual region continues while scenario disk ACK is blocked");release.countDown();idle(this::chosen);});
    }
    void holdJournal()throws Exception{
        var cl=Class.forName("ru.neverland.core.AtomicFiles",true,plugin.getClass().getClassLoader());var f=cl.getDeclaredField("writers");f.setAccessible(true);@SuppressWarnings("unchecked")var locks=(Map<Path,ReentrantLock>)f.get(null);var path=plugin.getDataFolder().toPath().resolve("scenarios-data.yml").toAbsolutePath().normalize();var lock=locks.computeIfAbsent(path,k->new ReentrantLock());release=new CountDownLatch(1);var locked=new CountDownLatch(1);new Thread(()->{lock.lock();locked.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{lock.unlock();}},"scenario-native-delay").start();if(!locked.await(1,TimeUnit.SECONDS))throw new AssertionError("missing file fixture lock");
    }
    void chosen()throws Exception{
        check(row(first).choice().equals("crops")&&Math.abs(events.cropCancelChance(T)-.42)<1e-9&&Math.abs(events.productionMultiplier(T,"agrarian_complex")-.4)<1e-9,"ACK activates exact crop benefit and production drawback together");denied(()->scenarios.choose(mayor,first,"production"),"repeated direct choice cannot switch outcome");
        events.restart(town);check(events.active(T).instanceId().equals(first)&&row(first).choice().equals("crops"),"admin restart retains event identity and accepted choice");
        SuiteState.freeze(plugin.getName());pulse(10000,()->{check(row(first).phase()==Phase.RESPONSE&&scenarios.effects(first).equals(Mix.neutral()),"module freeze suspends optional effects and phase progression");SuiteState.thaw(plugin.getName());check(scenarios.effects(first).equals(new Mix(.6,1.2)),"module resume restores both frozen terms");
            var catalog=(ScenarioCatalog)field(scenarios,"catalog");set(scenarios,"catalog",new ScenarioCatalog(false,catalog.definitions()));check(scenarios.effects(first).equals(Mix.neutral())&&!scenarios.canChoose(mayor,row(first)),"optional feature disable is neutral and closes new choices");set(scenarios,"catalog",catalog);
            int goal=events.active(T).goal();events.contribute(town,(goal*70+99)/100);pulse(0,()->{check(row(first).phase()==Phase.RECOVERY&&Math.abs(events.cropCancelChance(T)-.56)<1e-9&&Math.abs(events.productionMultiplier(T,"agrarian_complex")-.45)<1e-9,"actual submitted progress moves to saved recovery consequences");cancelled();});});
    }
    void cancelled()throws Exception{
        check(events.cancel(town)&&events.history(T).isEmpty(),"administrator cancellation adds no main failure history");
        check(events.cropCancelChance(T)==0&&events.productionMultiplier(T,"agrarian_complex")==1,"cancel removes effects immediately before optional closing ACK");
        pulse(0,()->{
            check(row(first).phase()==Phase.CANCELLED&&row(first).outcome().equals("UNAVAILABLE"),"optional cancellation is terminal and neutral");
            start("flood");second=events.active(T).instanceId();
            check(!second.equals(first),"next main event never reuses cancelled scenario identity");
            pulse(0,()->pulse(1100,this::secondDecision));
        });
    }
    void secondDecision()throws Exception{
        await(scenarios.choose(mayor,second,"production"),chosen->in(()->{
            events.pause(town,true);
            Files.writeString(proof.resolve("expected.txt"),first+"\n"+second+"\n"+row(second).chosen()+"\n"+row(second).worked()+"\n"+events.active(T).startedAt()+"\n");
            finishScenario();
        }));
    }
    void resumed()throws Exception{
        town=TownyAPI.getInstance().getTown(T);var lines=Files.readAllLines(proof.resolve("expected.txt"));first=UUID.fromString(lines.get(0));second=UUID.fromString(lines.get(1));check(events.active(T).instanceId().equals(second)&&events.active(T).startedAt()==Long.parseLong(lines.get(4)),"full JVM restart preserves actual event identity and historical start");check(events.active(T).paused()&&row(second).choice().equals("production")&&row(second).chosen()==Long.parseLong(lines.get(2))&&row(second).worked()==Long.parseLong(lines.get(3)),"restart retains paused work time actor and exactly one accepted choice");check(row(first).phase()==Phase.CANCELLED&&events.history(T).isEmpty(),"neutral old cancellation survives restart without failure history");
        String audit="";try(var paths=Files.walk(plugin.getDataFolder().toPath().resolve("audit"))){for(var p:paths.filter(p->p.toString().endsWith(".tsv")).toList())audit+=Files.readString(p);}check(audit.contains("CITY_EVENT_PLAN")&&audit.contains(second.toString()),"choice audit queue drains to durable categorized journal before shutdown");
        events.pause(town,false);check(Math.abs(events.cropCancelChance(T)-.72)<1e-9&&Math.abs(events.productionMultiplier(T,"agrarian_complex")-.76)<1e-9,"resumed flood uses its exact saved crop penalty and production benefit");events.contribute(town,events.active(T).goal());pulse(0,()->{check(row(second).phase()==Phase.COMPLETED&&row(second).outcome().equals("SUCCESS")&&events.history(T).size()==1,"actual main success creates one optional terminal result");check(events.history(T).getFirst().instanceId().equals(second),"main terminal history retains persistent scenario linkage");denied(()->scenarios.choose(mayor,second,"production"),"completed choice cannot replay effects or external rewards");
            start("drought");UUID third=events.active(T).instanceId();pulse(0,()->{events.resolve(town,false);pulse(0,()->{check(row(third).phase()==Phase.COMPLETED&&row(third).outcome().equals("FAILURE")&&events.history(T).size()==2,"main failure remains one result and optional layer adds no duplicate");finishScenario();});});});
    }
    void corrupt()throws Exception{
        town=TownyAPI.getInstance().getTown(T);check(!scenarios.ready()&&!scenarios.problem().isBlank(),"corrupt optional data and configuration suspend only new scenario decisions");check(Files.readString(plugin.getDataFolder().toPath().resolve("scenarios-data.yml")).equals("scenarios: [broken"),"corrupt optional evidence remains untouched");start("drought");check(events.cropCancelChance(T)==.7&&events.productionMultiplier(T,"agrarian_complex")==.5,"ordinary drought retains original effects while optional journal is unreadable");check(events.cancel(town)&&events.history(T).size()==2,"ordinary neutral cancellation still works with unavailable scenarios");start("fire");check(events.fireActive(T),"ordinary fire starts independently of corrupt scenarios");events.cancel(town);check(events.fires().damage(T).isEmpty(),"fire cancellation invents no new repair debt");start("raid");check(events.active(T).raid()!=null&&events.active(T).raid().wave()>=0,"ordinary raid keeps persisted wave system without scenario dependency");events.cancel(town);check(plugin.isEnabled(),"all ordinary operations leave Events enabled after optional failures");finishScenario();
    }
    void finishScenario()throws Exception{TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve(phase+"-passed.txt"),"PASS "+checks+" assertions; actual Folia Towny Events service, native plan inventory, real main event contribution/result and persistent identity, delayed file ACK, neutral admin pause/restart/cancel, full JVM recovery and corrupt optional isolation. Explicit offline Player/InventoryView/EntityScheduler and elapsed sample fixtures; no live player/network claim.\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
