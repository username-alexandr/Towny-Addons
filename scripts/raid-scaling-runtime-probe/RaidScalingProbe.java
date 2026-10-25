package ru.neverland.probe;

import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.SuiteState;
import ru.neverland.mintevents.service.*;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.api.TownyBuildsApi;

/** Test-only plugin. Creates a fixture city ONLY in a marked, loopback-bound disposable server. */
public final class RaidScalingProbe extends JavaPlugin {
    final List<String> checks = new ArrayList<>();
    final UUID townId = UUID.nameUUIDFromBytes("raid-development-fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    World world; Town town; JavaPlugin eventsPlugin; EventService events; DataStore data; TownyBuildsApi api;
    Path proof; boolean restart, done; int expectedRemaining; RaidScaling.Profile frozen;
    Object field(Object o,String name)throws Exception {var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    void check(boolean value,String name){if(!value)throw new AssertionError(name);checks.add("PASS "+name);}
    void close(double value,double expected,String name){check(Math.abs(value-expected)<0.000001,name+" ["+value+"]");}
    void later(Runnable r){Bukkit.getRegionScheduler().runDelayed(this,new Location(world,8,0,8),task->{synchronized(SuiteState.lock()){try{r.run();}catch(Throwable e){finish(e);}}},5);}
    @Override public void onEnable(){
        if(!Files.exists(Path.of("ALLOW_DISPOSABLE_RAID_SCALING_PROBE"))||!getServer().getIp().equals("127.0.0.1")){getServer().getPluginManager().disablePlugin(this);return;}
        proof=getDataFolder().toPath();world=Bukkit.getWorlds().getFirst();
        Bukkit.getGlobalRegionScheduler().run(this,t->{world.setTime(18000);world.getChunkAtAsync(0,0).whenComplete((chunk,error)->{if(error!=null){finish(error);return;}later(()->{try{setup();if(restart)restored();else initial();}catch(Throwable e){finish(e);}});});});
    }
    void setup()throws Exception{
        Files.createDirectories(proof);
        eventsPlugin=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyEvents");
        var builds=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds");
        check(eventsPlugin.isEnabled()&&builds.isEnabled(),"Events and Builds enabled on actual Folia");
        check(eventsPlugin.getPluginMeta().getVersion().equals("0.4.10"),"release 0.4.10 loaded");
        events=(EventService)field(eventsPlugin,"events");data=(DataStore)field(builds,"dataStore");
        api=Bukkit.getServicesManager().load(TownyBuildsApi.class);check(api!=null,"real Builds public API available");
        var u=TownyUniverse.getInstance();town=u.getTown(townId);restart=town!=null;
        if(!restart){
            u.newTownInternal("RaidScalingTown",townId);town=u.getTown(townId);
            var r=u.getDataSource().newResident("RaidFixtureMayor",UUID.nameUUIDFromBytes("raid-fixture-mayor".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            r.setTown(town);town.setMayor(r);
            var claim=new TownBlock(0,0,TownyAPI.getInstance().getTownyWorld(world));u.addTownBlock(claim);claim.setTown(town);town.setHomeBlock(claim);
            town.setSpawn(new Location(world,8,world.getHighestBlockYAt(8,8)+1,8));claim.save();r.save();town.save();
        }
    }
    void initial()throws Exception{
        var empty=events.raidPreview(town);check(empty.source().equals("development")&&empty.score()==0,"empty city uses baseline through live API");
        close(empty.countMultiplier(),1,"new city count");
        var yaml=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(eventsPlugin.getDataFolder().toPath().resolve("raid-scaling.yml").toFile());
        var weights=yaml.getConfigurationSection("project-weights");
        var ordinary=api.supportedProjects().stream().filter(x->weights==null||!weights.contains(x)).sorted().limit(40).toList();
        check(ordinary.size()==40,"forty actual ordinary building definitions available");
        for(String id:ordinary.subList(0,10))data.town(townId).setLevel(id,5);
        var medium=events.raidPreview(town);close(medium.score(),50,"completed building levels read without disk refresh");close(medium.healthMultiplier(),1.25,"medium city health factor");
        for(String id:ordinary)data.town(townId).setLevel(id,5);
        var rich=events.raidPreview(town);close(rich.score(),200,"developed city score");close(rich.countMultiplier(),2.5,"developed city count factor");close(rich.healthMultiplier(),2,"developed city health factor");close(rich.damageMultiplier(),1.5,"developed city damage factor");
        SuiteState.freeze("NeverLandTownyBuilds");try{var fallback=events.raidPreview(town);check(fallback.source().equals("unavailable")&&fallback.countMultiplier()==1,"paused Builds safely falls back to baseline");}finally{SuiteState.thaw("NeverLandTownyBuilds");}
        data.markDirty();data.saveOrThrow();
        var registry=(EventRegistry)field(eventsPlugin,"registry");
        check(events.startEvent(town,registry.get("raid"),true),"real raid starts");frozen=events.active(townId).raid().profile();
        check(frozen.equals(rich),"start snapshots exact development profile");
        awaitMobs(0,false);
    }
    void awaitMobs(int attempt,boolean resumed){
        var active=events.active(townId);check(active!=null,"raid remains active");
        var state=active.raid();var mobs=Arrays.stream(world.getChunkAt(0,0).getEntities()).filter(e->e instanceof LivingEntity&&state.owns(e.getUniqueId())).map(e->(LivingEntity)e).toList();
        if(mobs.isEmpty()){if(attempt>40)throw new AssertionError("no native raid mobs spawned");later(()->awaitMobs(attempt+1,resumed));return;}
        try{
            var catalog=(RaidCatalog)field(events,"raidCatalog");
            var wave=state.waveDefinition(state.wave(),catalog);var spec=catalog.mob(wave.mobs().getFirst());var mob=mobs.getFirst();
            double mitigation=1-Math.max(0,Math.min(.8,active.protection()))*.4;
            close(mob.getAttribute(Attribute.MAX_HEALTH).getBaseValue(),spec.health()*wave.healthMultiplier()*mitigation,"native mob health includes stored scaling and city defense");
            close(mob.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue(),spec.damage()*wave.damageMultiplier()*mitigation,"native melee damage scaled");
            close(events.raidRangedMultiplier(mob),wave.damageMultiplier()*(1-active.protection()*.4),"native ranged multiplier stored");
            check(state.aliveCount()<=18,"concurrent enemy cap retained");
            if(resumed){check(state.remaining()==expectedRemaining,"resume spawns only saved survivors");check(events.pause(town,true),"fixture pauses after restart check");finish(null);return;}
            check(state.wave()==1&&state.remaining()==10,"first native wave expands from four to ten enemies");
            mob.setHealth(0);check(state.remaining()==9,"real death removes exactly one enemy");
            expectedRemaining=state.remaining();
            data.town(townId).loadLevels(Map.of());data.markDirty();data.saveOrThrow();
            events.reloadRuntime();check(events.raidPreview(town).score()==0,"next raid responds to changed building levels");
            check(events.active(townId).raid().profile().equals(frozen),"reload and changed buildings preserve active raid profile");
            check(events.active(townId).raid().waveDefinition(10,catalog).mobs().size()==41,"future tenth wave stays in frozen plan");
            check(events.pause(town,true),"pause persists remaining enemies");check(events.restart(town),"administrator timer restart succeeds");
            check(events.active(townId).raid().remaining()==9&&events.active(townId).raid().profile().equals(frozen),"admin restart preserves kills and difficulty");
            check(events.pause(town,true),"raid stored paused before process restart");
            Files.writeString(proof.resolve("expected-remaining.txt"),Integer.toString(expectedRemaining));finish(null);
        }catch(Throwable e){finish(e);}
    }
    void restored()throws Exception{
        expectedRemaining=Integer.parseInt(Files.readString(proof.resolve("expected-remaining.txt")));
        var active=events.active(townId);check(active!=null&&active.paused(),"active paused raid restored after full server restart");
        check(events.raidPreview(town).score()==0,"changed Buildings data persisted");
        close(active.raid().profile().score(),200,"raid retained original city development snapshot");
        check(active.raid().remaining()==expectedRemaining&&expectedRemaining==9,"defeated enemy not recreated after process restart");
        var catalog=(RaidCatalog)field(events,"raidCatalog");check(active.raid().waveDefinition(10,catalog).mobs().size()==41,"unstarted tenth wave survives full restart");
        check(events.pause(town,false),"persisted raid resumes");awaitMobs(0,true);
    }
    void finish(Throwable failure){
        if(done)return;done=true;
        try{
            Files.createDirectories(proof);String phase=restart?"restart":"initial";
            if(failure==null){data.saveOrThrow();TownyUniverse.getInstance().getDataSource().saveAll();TownyUniverse.getInstance().getDataSource().saveQueues();Files.writeString(proof.resolve(phase+"-passed.txt"),"PASS "+checks.size()+" checks\n");}
            else{getLogger().log(java.util.logging.Level.SEVERE,"RAID_SCALING_FAIL",failure);var trace=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(trace));Files.writeString(proof.resolve(phase+"-failed.txt"),trace.toString());}
            Files.write(proof.resolve(phase+"-checks.txt"),checks);
        }catch(Exception e){e.printStackTrace();}
        Bukkit.getGlobalRegionScheduler().runDelayed(this,t->Bukkit.shutdown(),2);
    }
}
