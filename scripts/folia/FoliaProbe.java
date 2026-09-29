package ru.neverland.validation;

import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable native-server probe. Never distributed in dist/plugins. */
public final class FoliaProbe extends JavaPlugin {
    private final List<String> passed=new CopyOnWriteArrayList<>(),failed=new CopyOnWriteArrayList<>();
    private final AtomicInteger remaining=new AtomicInteger(2);
    private final Set<String> regionThreads=ConcurrentHashMap.newKeySet();
    private Plugin control;
    private Class<?> scheduler,state,worldTasks;
    private Object lock;
    private final AtomicInteger guarded=new AtomicInteger(),awards=new AtomicInteger();
    private Class<?> outbox;
    @Override public void onEnable(){
        getCommand("nltprobeaward").setExecutor((sender,cmd,label,args)->{check("reward runs on global scheduler",Bukkit.isGlobalTickThread());awards.incrementAndGet();return true;});
        Bukkit.getGlobalRegionScheduler().runDelayed(this,t->begin(),60);
        Bukkit.getGlobalRegionScheduler().runDelayed(this,t->finish(),500);
    }
    private void check(String name,boolean condition){(condition?passed:failed).add(name);getLogger().info((condition?"PASS ":"FAIL ")+name);}
    private void error(String name,Throwable e){failed.add(name+": "+e);getLogger().log(java.util.logging.Level.SEVERE,name,e);}
    private Object invoke(Class<?> type,String name,Class<?>[] signature,Object...args)throws Exception{return type.getMethod(name,signature).invoke(null,args);}
    private void begin(){try{
        var plugins=Arrays.stream(Bukkit.getPluginManager().getPlugins()).filter(p->p.getName().startsWith("NeverLandTowny")).toList();
        check("40 addon artifacts loaded",plugins.size()==40);
        for(Plugin p:plugins)check(p.getName()+" enabled",p.isEnabled());
        control=Bukkit.getPluginManager().getPlugin("NeverLandTownyControl");
        scheduler=Class.forName("ru.neverland.core.SuiteScheduler",true,control.getClass().getClassLoader());
        state=Class.forName("ru.neverland.core.SuiteState",true,control.getClass().getClassLoader());
        worldTasks=Class.forName("ru.neverland.core.WorldTasks",true,control.getClass().getClassLoader());
        lock=invoke(state,"lock",new Class<?>[0]);
        for(Plugin p:plugins){Class<?> copy=Class.forName("ru.neverland.core.SuiteState",true,p.getClass().getClassLoader());check(p.getName()+" shares ledger",invoke(copy,"lock",new Class<?>[0])==lock);}
        outbox=Class.forName("ru.neverland.core.CommandOutbox",true,control.getClass().getClassLoader());
        List<String> commands=List.of("nltprobeaward","nlt_probe_missing_command");
        invoke(outbox,"enqueue",new Class<?>[]{Plugin.class,String.class,List.class},this,"probe-dedup",commands);
        invoke(outbox,"enqueue",new Class<?>[]{Plugin.class,String.class,List.class},this,"probe-dedup",commands);
        check("global owner",Bukkit.isGlobalTickThread());
        World world=Bukkit.getWorlds().get(0);
        try{world.save();check("world save from global scheduler",true);}catch(Throwable e){error("world save from global scheduler",e);}
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"nltmodules list");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"nltaudit status");
        // Schedule through the shaded scheduler from two regions and verify every callback holds the common monitor.
        for(int i=0;i<2;i++){
            final int n=i;Location location=new Location(world,i*8192+8,70,i*8192+8);
            world.getChunkAtAsync(location).whenComplete((chunk,ex)->{
                if(ex!=null){error("load region "+n,ex);remaining.decrementAndGet();return;}
                Bukkit.getRegionScheduler().run(this,location,t->{
                    chunk.addPluginChunkTicket(this);
                    try{invoke(scheduler,"region",new Class<?>[]{Plugin.class,Location.class,Runnable.class},control,location,(Runnable)()->region(n,location));}
                    catch(Throwable e){error("region dispatch "+n,e);remaining.decrementAndGet();}
                });
            });
        }
        Plugin quests=Bukkit.getPluginManager().getPlugin("NeverLandTownyQuests");
        invoke(state,"freeze",new Class<?>[]{String.class},quests.getName());
        Object instance=invoke(scheduler,"get",new Class<?>[0]);
        scheduler.getMethod("runTaskLater",Plugin.class,Runnable.class,long.class).invoke(instance,quests,(Runnable)guarded::incrementAndGet,2L);
        Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{try{
            check("frozen addon task suppressed",guarded.get()==0);
            invoke(state,"thaw",new Class<?>[]{String.class},quests.getName());
        }catch(Throwable e){error("freeze barrier",e);}},8);
    }catch(Throwable e){error("startup probe",e);}}
    private void region(int number,Location at){try{
        check("region "+number+" owns location",Bukkit.isOwnedByCurrentRegion(at));
        check("region "+number+" is not global",!Bukkit.isGlobalTickThread());
        check("region "+number+" holds ledger",Thread.holdsLock(lock));
        regionThreads.add(Thread.currentThread().getName());
        at.getBlock().setType(Material.STONE);
        check("region "+number+" block mutation",at.getBlock().getType()==Material.STONE);
        ArmorStand entity=at.getWorld().spawn(at.clone().add(0,2,0),ArmorStand.class,e->{e.setGravity(false);e.setVisible(false);});
        invoke(scheduler,"entityLater",new Class<?>[]{Plugin.class,Entity.class,Runnable.class,Runnable.class,long.class},control,entity,(Runnable)()->{
            try{
                check("entity "+number+" owner",Bukkit.isOwnedByCurrentRegion(entity));
                check("entity "+number+" holds ledger",Thread.holdsLock(lock));
                Location target=at.clone().add(number==0?8192:-8192,0,number==0?8192:-8192);
                entity.teleportAsync(target).whenComplete((ok,err)->{
                    if(err!=null||!Boolean.TRUE.equals(ok)){error("async teleport "+number,err==null?new IllegalStateException("declined"):err);remaining.decrementAndGet();return;}
                    try{invoke(scheduler,"entity",new Class<?>[]{Plugin.class,Entity.class,Runnable.class},control,entity,(Runnable)()->{
                        check("teleported entity "+number+" owner",Bukkit.isOwnedByCurrentRegion(entity));
                        check("teleported entity "+number+" destination",entity.getLocation().distanceSquared(target)<1);
                        check("loaded entity index "+number,find(entity.getUniqueId())==entity);
                        try{invoke(scheduler,"entityLater",new Class<?>[]{Plugin.class,Entity.class,Runnable.class,Runnable.class,long.class},control,entity,(Runnable)()->error("retired task ran",new AssertionError()),(Runnable)()->{check("retired callback "+number,true);remaining.decrementAndGet();},20L);entity.remove();}
                        catch(Throwable e){error("retirement "+number,e);remaining.decrementAndGet();}
                    });}catch(Throwable e){error("post teleport "+number,e);remaining.decrementAndGet();}
                });
            }catch(Throwable e){error("entity "+number,e);remaining.decrementAndGet();}
        },(Runnable)()->{error("early retire "+number,new AssertionError());remaining.decrementAndGet();},1L);
    }catch(Throwable e){error("region "+number,e);remaining.decrementAndGet();}}
    private Entity find(UUID id){try{return (Entity)invoke(worldTasks,"find",new Class<?>[]{UUID.class},id);}catch(Exception e){throw new RuntimeException(e);}}
    private void finish(){
        if(control!=null&&control.isEnabled()){
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"nltmodules disable Quests");
            Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{
                Plugin addon=Bukkit.getPluginManager().getPlugin("NeverLandTownyQuests");
                check("admin module disable completes",addon!=null&&!addon.isEnabled());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"nltmodules enable Quests");
                check("enable waits for restart",addon!=null&&!addon.isEnabled());
                writeResult();
            },20);return;
        }
        writeResult();
    }
    private void writeResult(){
        check("reward command is issued once",awards.get()==1);
        try{
            var entries=(List<?>)invoke(outbox,"entries",new Class<?>[]{Plugin.class},this);int done=0,review=0;
            for(var e:entries){String status=(String)e.getClass().getMethod("state").invoke(e);if(status.equals("DONE"))done++;if(status.equals("REVIEW"))review++;}
            check("durable reward outcome",done==1&&review==1);
        }catch(Throwable e){error("reward receipts",e);}
        check("both region chains completed",remaining.get()==0);
        check("region worker observed",!regionThreads.isEmpty());
        try{getDataFolder().mkdirs();String data="passed="+passed.size()+"\nfailed="+failed.size()+"\n"+String.join("\n",failed)+"\n";Files.writeString(getDataFolder().toPath().resolve("result.txt"),data);getLogger().info("FOLIA_PROBE_COMPLETE "+data.replace('\n',' '));}
        catch(Exception e){error("write result",e);}
    }
}
