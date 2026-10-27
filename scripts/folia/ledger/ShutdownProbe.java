package ru.neverland.validation;

import com.palmergames.bukkit.towny.TownyUniverse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.mintevents.model.*;
import ru.neverland.mintevents.service.*;

/** Disposable fixture: stop with a live, partially completed raid; verify the next boot. */
public final class ShutdownProbe extends JavaPlugin {
    private final List<String> checks=new CopyOnWriteArrayList<>();
    private final String runId=System.getProperty("neverland.probe.run","legacy");
    private final UUID city=UUID.nameUUIDFromBytes("shutdown-raid-fixture".getBytes(StandardCharsets.UTF_8));
    private JavaPlugin events;
    private EventService service;
    private EventRepository repository;
    private Location anchor;
    private Entity body;
    private Path proof,fixture;
    private boolean ready;
    private void check(boolean ok,String label){checks.add((ok?"PASS ":"FAIL ")+label);if(!ok)throw new AssertionError(label);}
    private Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private void fail(Throwable error){checks.add("FAIL "+error);getLogger().log(java.util.logging.Level.SEVERE,"SHUTDOWN_PROBE_FAIL",error);}
    @Override public void onEnable(){
        if(!Files.exists(Path.of("ALLOW_DISPOSABLE_SHUTDOWN_PROBE"))||!getServer().getIp().equals("127.0.0.1")){getServer().getPluginManager().disablePlugin(this);return;}
        proof=getDataFolder().toPath().resolve("runs").resolve(runId);fixture=getDataFolder().toPath().resolve("fixture.yml");
        SuiteScheduler.get().runTaskLater(this,()->{try{prepare();}catch(Throwable error){fail(error);}},300);
    }
    private void prepare()throws Exception{
        events=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyEvents");
        check(events!=null&&events.isEnabled(),"Events enabled before shutdown scenario");
        service=(EventService)field(events,"events");repository=(EventRepository)field(service,"repository");
        var universe=TownyUniverse.getInstance();var town=universe.getTown(city);boolean restart=Files.exists(fixture);
        if(!restart){
            check(town==null,"isolated raid town does not preexist");
            universe.newTownInternal("ShutdownTown",city);town=universe.getTown(city);
            var mayor=universe.getDataSource().newResident("ShutdownMayor",UUID.nameUUIDFromBytes("shutdown-mayor".getBytes(StandardCharsets.UTF_8)));
            mayor.setTown(town);town.setMayor(mayor);mayor.save();town.save();
            var definition=service.registry().all().stream().filter(d->d.mode()==EventMode.RAID).findFirst().orElseThrow();
            check(service.startEvent(town,definition,true),"real EventService starts an active raid");
            var active=repository.active(city).reschedule(System.currentTimeMillis()+3_600_000L,0);repository.put(active);
            var catalog=(RaidCatalog)field(service,"raidCatalog");String mob=catalog.wave(1).mobs().get(0);
            active.raid().begin(List.of(mob,mob,mob));UUID defeated=UUID.randomUUID();active.raid().spawned(defeated);active.raid().died(defeated);active.addProgress(1);repository.changed();
        }else{
            check(town!=null,"town survives restart");var active=repository.active(city);var before=YamlConfiguration.loadConfiguration(fixture.toFile());
            check(active!=null&&!active.paused(),"unfinished event remains active after restart");
            check(active.progress()==1&&active.raid().wave()==1&&active.raid().remaining()==2,"completed kill and unfinished wave survive without duplication");
            check(active.raid().aliveCount()==0,"saved live enemy is queued once for recovery");
            check(!active.raid().generation().toString().equals(before.getString("generation")),"restart invalidates old entity generation");
        }
        anchor=new Location(Bukkit.getWorlds().get(0),8,70,8);
        final boolean restarted=restart;
        WorldTasks.loaded(this,anchor,()->{
            try{
                anchor.getChunk().addPluginChunkTicket(this);
                // Give normal Events load validation time to remove the previous raid body.
                SuiteScheduler.regionLater(this,anchor,()->{try{spawnAndCheck(restarted);}catch(Throwable error){fail(error);}},40);
            }catch(Throwable error){fail(error);}
        });
    }
    private void spawnAndCheck(boolean restart)throws Exception{
        var old=YamlConfiguration.loadConfiguration(fixture.toFile());
        if(restart){
            UUID prior=UUID.fromString(old.getString("body")),foreign=UUID.fromString(old.getString("foreign"));
            var entities=Arrays.asList(anchor.getChunk().getEntities());
            check(entities.stream().noneMatch(e->e.getUniqueId().equals(prior)),"normal chunk-load validation removes stale raid body");
            check(entities.stream().anyMatch(e->e.getUniqueId().equals(foreign)),"unrelated persistent entity survives cleanup and restart");
        }
        var active=repository.active(city);
        body=anchor.getWorld().spawn(anchor,Zombie.class,z->{
            z.getPersistentDataContainer().set(new NamespacedKey(events,"raid_town"),PersistentDataType.STRING,city.toString());
            z.getPersistentDataContainer().set(new NamespacedKey(events,"raid_generation"),PersistentDataType.STRING,active.raid().generation().toString());
            z.setPersistent(true);z.setRemoveWhenFarAway(false);z.setAI(false);z.setGravity(false);z.setInvulnerable(true);z.setSilent(true);
        });
        active.raid().spawned(body.getUniqueId());repository.changed();repository.save();
        check(active.raid().aliveCount()==1&&active.raid().remaining()==2,"shutdown fixture contains one live and one pending enemy");
        old.set("body",body.getUniqueId().toString());old.set("generation",active.raid().generation().toString());
        if(!restart){
            var foreign=anchor.getWorld().spawn(anchor.clone().add(3,0,0),ArmorStand.class,e->{e.setPersistent(true);e.setGravity(false);e.setInvulnerable(true);});
            old.set("foreign",foreign.getUniqueId().toString());
        }
        Files.createDirectories(fixture.getParent());old.save(fixture.toFile());
        check(WorldTasks.cleanupOwner(this)!=null,"live server still provides cleanup scheduler owner");
        ready=true;getLogger().info("SHUTDOWN_PROBE_READY run_id="+runId);
    }
    @Override public void onDisable(){
        if(proof==null)return;
        try{
            check(ready,"active raid prepared before server stop");
            check(Bukkit.isStopping(),"probe observes actual full server shutdown");
            check(events.isEnabled(),"Events stays enabled until its real shutdown callback");
            check(repository.active(city)!=null&&repository.active(city).progress()==1,"shutdown begins with intact active event");
            WorldTasks.eachEntityCleanup(events,e->{throw new AssertionError("cleanup must not run after ticking stops");});
            check(WorldTasks.cleanupOwner(events)==null,"stopping server has no cleanup scheduler owner");
            check(SuiteScheduler.region(events,anchor,()->{}).isCancelled(),"region submission returns cancelled handle during real Folia stop");
            check(SuiteScheduler.entity(events,body,()->{}).isCancelled(),"entity submission returns cancelled handle during real Folia stop");
            check(SuiteScheduler.get().runTask(events,()->{}).isCancelled(),"global submission returns cancelled handle during real Folia stop");
        }catch(Throwable error){fail(error);}
        finally{
            try{Files.createDirectories(proof);Files.write(proof.resolve("checks.txt"),checks);Files.writeString(proof.resolve("result.txt"),"run_id="+runId+"\nfailed="+checks.stream().filter(s->s.startsWith("FAIL")).count()+"\nchecks="+checks.size()+"\n");}
            catch(Exception error){getLogger().log(java.util.logging.Level.SEVERE,"Cannot write shutdown proof",error);}
        }
    }
}
