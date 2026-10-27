package ru.neverland.runtime;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.townysieges.*;
import ru.neverland.townybuilds.construction.*;
import ru.neverland.core.SuiteState;

/** Real Folia/Towny/TNT/SQLite/displays. Explicit Access double isolates world damage from player login/Army. */
public class TntRuntimeProbe extends JavaPlugin implements Listener {
    static UUID id(String s){return UUID.nameUUIDFromBytes(("tnt-runtime-046-"+s).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static final UUID DEF=id("defender"),ATT=id("attacker"),ACTOR=id("actor"),MAYOR=id("mayor");
    NeverLandTownySiegesPlus plugin;TntRaidEngine engine;RaidStore store;World world;Location origin;Town defender,attacker;
    Path proof;boolean restart,allow=true,cancelNext;int checks;TNTPrimed first;CountDownLatch gate;
    interface Work{void run()throws Exception;}
    static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String n,Object value)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,value);}
    static Object call(Object o,String n,Class<?>[] types,Object...args)throws Exception{var m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o,args);}
    void check(boolean ok,String label)throws Exception{if(!ok)throw new AssertionError(label);checks++;Files.writeString(proof.resolve((restart?"restart":"first")+"-checks.txt"),"PASS "+label+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);getLogger().info("CHECK "+label);}
    void later(long ticks,Work work){Bukkit.getRegionScheduler().runDelayed(this,origin,t->{if(!SuiteState.tryRun("TNT native probe",()->{try{work.run();}catch(Throwable e){fail(e);}}))later(1,work);},ticks);}
    <T>void await(CompletionStage<T> future,Consumer<T> then){future.whenComplete((value,error)->{if(error!=null){fail(error);return;}later(1,()->then.accept(value));});}
    void fail(Throwable e){if(gate!=null)gate.countDown();getLogger().log(java.util.logging.Level.SEVERE,"TNT PROBE FAILED",e);try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("failed.txt"),e.toString());}catch(Exception ignored){}Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
    @Override public void onEnable(){
        if(!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RAID_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){Bukkit.getPluginManager().disablePlugin(this);return;}
        proof=getDataFolder().toPath();Bukkit.getPluginManager().registerEvents(this,this);
        Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{try{Files.createDirectories(proof);restart=Files.exists(proof.resolve("first-passed.txt"));world=Bukkit.getWorlds().getFirst();origin=new Location(world,15.5,160.5,8.5);
            var chunks=new ArrayList<CompletableFuture<?>>();for(int x=-2;x<=3;x++)for(int z=-2;z<=2;z++)chunks.add(world.getChunkAtAsync(x,z));
            CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).whenComplete((v,e)->{if(e!=null)fail(e);else later(5,()->setup());});
        }catch(Throwable e){fail(e);}},30);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void cancel(EntityExplodeEvent e){if(cancelNext){cancelNext=false;e.setCancelled(true);}}
    Block block(int x,int y,int z){return world.getBlockAt(x,y,z);}
    RaidStore.Key key(Block b){return new RaidStore.Key(world.getUID(),b.getX(),b.getY(),b.getZ());}
    CompletionStage<RaidStore.Health> hp(Block b){return store.health(key(b),b.getType().name(),plugin.raids().settings().hp(b.getType().name()),System.currentTimeMillis(),1800);}
    void door(int x,int z){for(var half:Bisected.Half.values()){var data=(Door)Material.OAK_DOOR.createBlockData();data.setHalf(half);block(x,160+(half==Bisected.Half.TOP?1:0),z).setBlockData(data,false);}}
    TNTPrimed tnt(boolean actual){var entity=world.spawn(origin,TNTPrimed.class);entity.getPersistentDataContainer().set(new NamespacedKey(plugin,"raid_tnt_actor"),PersistentDataType.STRING,ACTOR.toString());entity.setFuseTicks(actual?2:10000);entity.setGravity(false);entity.setVelocity(new org.bukkit.util.Vector());return entity;}
    EntityExplodeEvent blast(TNTPrimed entity){var e=new EntityExplodeEvent(entity,origin,new ArrayList<>(List.of(block(15,160,9),block(19,160,8),block(14,160,8),block(16,160,8))),0,ExplosionResult.DESTROY);Bukkit.getPluginManager().callEvent(e);return e;}
    void setup()throws Exception{
        plugin=(NeverLandTownySiegesPlus)Bukkit.getPluginManager().getPlugin("NeverLandTownySiegesPlus");engine=plugin.tnt();store=plugin.raids().store();check(plugin.isEnabled()&&store.healthy(),"SiegesPlus 0.3.0 and actual SQLite enabled");
        check(Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds").getDescription().getVersion().equals("0.8.41"),"Builds 0.8.41 enabled");
        var u=TownyUniverse.getInstance();if(!restart){u.newTownInternal("TntDefenders",DEF);u.newTownInternal("TntAttackers",ATT);
            defender=TownyAPI.getInstance().getTown(DEF);attacker=TownyAPI.getInstance().getTown(ATT);
            for(var entry:Map.of(ACTOR,"TntRaider",MAYOR,"TntMayor").entrySet()){var r=u.getDataSource().newResident(entry.getValue(),entry.getKey());r.setTown(entry.getKey().equals(ACTOR)?attacker:defender);r.save();}
            defender.setMayor(TownyAPI.getInstance().getResident(MAYOR));attacker.setMayor(TownyAPI.getInstance().getResident(ACTOR));
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){var c=new TownBlock(x,z,u.getWorld(world.getName()));u.addTownBlock(c);c.setTown(defender);if(x==0&&z==0)defender.setHomeBlock(c);c.save();}
            var c=new TownBlock(3,0,u.getWorld(world.getName()));u.addTownBlock(c);c.setTown(attacker);attacker.setHomeBlock(c);c.save();
            defender.setSpawn(origin);attacker.setSpawn(new Location(world,50.5,160,8.5));defender.setNeutral(false);attacker.setNeutral(false);defender.save();attacker.save();
        }else{defender=TownyAPI.getInstance().getTown(DEF);attacker=TownyAPI.getInstance().getTown(ATT);}
        for(int x=-2;x<=3;x++)for(int z=-2;z<=2;z++)world.getChunkAt(x,z).addPluginChunkTicket(this);
        check(!plugin.raids().healthy(),"real controller fails closed without Army provider");
        var relation=RaidController.class.getDeclaredMethod("relations",Town.class,Town.class);relation.setAccessible(true);boolean denied=false;defender.setNeutral(true);try{relation.invoke(plugin.raids(),attacker,defender);}catch(InvocationTargetException expected){denied=true;}check(denied,"real relation guard rejects neutral town");defender.setNeutral(false);
        boolean same=false;try{relation.invoke(plugin.raids(),attacker,attacker);}catch(InvocationTargetException expected){same=true;}check(same,"real relation guard rejects own town");
        if(!restart){
            // Towny command creation supplies this timestamp; newTownInternal alone leaves it unset.
            attacker.setRegistered(System.currentTimeMillis());defender.setRegistered(System.currentTimeMillis());
            boolean shield=false;try{relation.invoke(plugin.raids(),attacker,defender);}catch(InvocationTargetException expected){shield=expected.getCause().getMessage().contains("щит новичка");}check(shield,"real newcomer shield rejects a raid");
            attacker.setRegistered(System.currentTimeMillis()-172800000L);defender.setRegistered(System.currentTimeMillis()-172800000L);
            relation.invoke(plugin.raids(),attacker,defender);check(true,"real relation guard accepts two unshielded unrelated towns");
            u.getDataSource().newNation("TntUnion",id("nation"));var nation=TownyAPI.getInstance().getNation(id("nation"));attacker.setNation(nation);defender.setNation(nation);
            boolean allied=false;try{relation.invoke(plugin.raids(),attacker,defender);}catch(InvocationTargetException expected){allied=expected.getCause().getMessage().contains("Союзный");}check(allied,"real Towny alliance blocks raid");attacker.setNation(null);defender.setNation(null);
            boolean offline=false;try{call(plugin.raids(),"defenders",new Class<?>[]{Town.class},defender);}catch(InvocationTargetException expected){offline=expected.getCause().getMessage().contains("защитников");}check(offline,"real defender-online threshold prevents offline raid");
            attacker.save();defender.save();
        }
        set(engine,"access",new TntRaidEngine.Access(){public boolean ready(){return store.healthy();}public boolean managed(UUID town){return town.equals(DEF);}public boolean valid(RaidController.Permit permit){return allow&&permit.defender().equals(DEF);}public void authorize(UUID actor,UUID town,Consumer<RaidController.Permit> permitted){if(allow&&actor.equals(ACTOR)&&town.equals(DEF))permitted.accept(new RaidController.Permit(ACTOR,ATT,DEF,id("raid"),System.currentTimeMillis(),false));}});
        label();if(restart){restartChecks();return;}
        block(15,160,9).setType(Material.STONE,false);block(19,160,8).setType(Material.STONE,false);block(15,159,8).setType(Material.OBSIDIAN,false);block(15,160,7).setType(Material.GLASS,false);block(15,161,8).setType(Material.BEDROCK,false);door(14,8);
        block(16,160,8).setType(Material.CHEST,false);((Chest)block(16,160,8).getState()).getBlockInventory().addItem(new ItemStack(Material.DIAMOND,3));
        first=tnt(true);later(45,()->firstChecks());
    }
    void label()throws Exception{
        var owner=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds");Object service=field(owner,"construction");Class<?> target=Class.forName("ru.neverland.townybuilds.construction.ConstructionService$PreviewTarget",true,owner.getClass().getClassLoader());var ctor=target.getDeclaredConstructors()[0];ctor.setAccessible(true);
        Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->m.getName().equals("getUniqueId")?MAYOR:null);
        var value=ctor.newInstance(origin,new BlueprintBlock(Material.STONE,BlockRole.RESIDENT,1),1.0,new ConstructionSite("town_hall",world.getUID(),15,160,8,BlockFace.NORTH,0,1,1,true),new ConstructionLayers.Layer(0,1,3,0,1,List.of()),"Камень × 1");
        call(service,"renderGuideDisplay",new Class<?>[]{Player.class,target},player,value);var display=(TextDisplay)((Map<?,?>)field(service,"guideDisplays")).get(MAYOR);
        check(display!=null&&display.isValid()&&Math.abs(display.getTransformation().getScale().x()-.45)<.0001&&display.getLineWidth()==200,"real construction TextDisplay uses 0.45 scale and 200px wrapping");
        check(!display.isPersistent()&&!display.isVisibleByDefault(),"guide remains private and temporary");
        owner.getConfig().set("settings.construction.guide-label-scale",Double.NaN);call(service,"renderGuideDisplay",new Class<?>[]{Player.class,target},player,value);check(Math.abs(display.getTransformation().getScale().x()-.45)<.0001,"invalid guide scale falls back safely");owner.getConfig().set("settings.construction.guide-label-scale",null);
        call(service,"removeGuide",new Class<?>[]{UUID.class},MAYOR);
    }
    void firstChecks(){await(hp(block(15,160,9)),v->{try{check(Math.abs(v.hp()-300)<.001,"real TNT contact blast applies 100 HP");}catch(Exception e){throw new CompletionException(e);}await(hp(block(19,160,8)),far->{try{check(Math.abs(far.hp()-375)<.001,"real TNT crossing chunk boundary applies 25 HP at 3.5 blocks");}catch(Exception e){throw new CompletionException(e);}await(hp(block(14,160,8)),d->{try{
            check(Math.abs(d.hp()-20)<.001,"both wooden door halves take only one 100 HP hit");check(block(15,160,7).getType().isAir(),"glass destroyed at zero HP");check(block(15,161,8).getType()==Material.BEDROCK,"bedrock protected");check(block(16,160,8).getType()==Material.CHEST&&((Chest)block(16,160,8).getState()).getBlockInventory().contains(Material.DIAMOND,3),"chest and contents protected from real TNT");
            blast(first);later(12,()->replayChecks());
        }catch(Exception e){throw new CompletionException(e);}});});});}
    void replayChecks(){await(hp(block(15,160,9)),v->{try{check(Math.abs(v.hp()-300)<.001,"replayed TNT entity UUID cannot damage twice");var second=tnt(false);var event=blast(second);check(event.blockList().stream().noneMatch(b->TownyAPI.getInstance().getTown(b.getLocation())==defender),"native blast cannot bypass HP in managed defender town");second.remove();later(20,()->secondChecks());}catch(Exception e){throw new CompletionException(e);}});}
    void secondChecks()throws Exception{
        check(block(14,160,8).getType().isAir()&&block(14,161,8).getType().isAir(),"second contact hit destroys both door halves together");
        cancelNext=true;var cancelled=tnt(false);check(blast(cancelled).isCancelled(),"foreign final cancellation retained");cancelled.remove();allow=false;var denied=tnt(false);blast(denied);denied.remove();later(15,()->deniedChecks());
    }
    void deniedChecks(){await(hp(block(15,160,9)),v->{try{check(Math.abs(v.hp()-200)<.001,"cancelled and unauthorized blasts leave HP unchanged");allow=true;replacement();}catch(Exception e){throw new CompletionException(e);}});}
    void replacement()throws Exception{
        door(14,8);var k=key(block(14,160,8));store.reset(k).thenCompose(v->store.damage(UUID.randomUUID(),ACTOR.toString(),ATT,DEF,System.currentTimeMillis(),1800,List.of(new RaidStore.Hit(k,DEF,"OAK_DOOR",0,120,100)))).whenComplete((v,e)->later(1,()->{
            gate=new CountDownLatch(1);((ThreadPoolExecutor)field(store,"worker")).execute(()->{try{if(!gate.await(10,TimeUnit.SECONDS))throw new AssertionError("test worker gate timeout");}catch(InterruptedException x){Thread.currentThread().interrupt();}});
            var entity=tnt(false);blast(entity);entity.remove();later(4,()->{Player actor=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->m.getName().equals("getUniqueId")?ACTOR:null);engine.broken(new BlockBreakEvent(block(14,160,8),actor));door(14,8);gate.countDown();later(20,()->replacementChecks());});
        }));
    }
    void replacementChecks()throws Exception{
        check(block(14,160,8).getType()==Material.OAK_DOOR&&block(14,161,8).getType()==Material.OAK_DOOR,"replaced door survives stale in-flight hit");
        await(hp(block(14,160,8)),v->{try{check(Math.abs(v.hp()-120)<.001,"replaced block starts with full HP");pause();}catch(Exception e){throw new CompletionException(e);}});
    }
    void pause()throws Exception{
        SuiteState.freeze(plugin.getName());var entity=tnt(false);blast(entity);entity.remove();later(12,()->{SuiteState.thaw(plugin.getName());await(hp(block(15,160,9)),v->{try{check(Math.abs(v.hp()-100)<.001,"module pause causes no additional HP loss");cancelWhilePaused();}catch(Exception e){throw new CompletionException(e);}});});
    }
    void cancelWhilePaused(){
        long now=System.currentTimeMillis();var raid=new RaidStore.Raid(id("cancelled-raid"),ATT,DEF,ACTOR,now,now,now+600000,now+86400000,"OPEN");
        await(store.start(raid),rows->{try{set(plugin.raids(),"raids",rows);SuiteState.freeze(plugin.getName());plugin.raids().cancel(Bukkit.getConsoleSender(),DEF);later(15,()->{check(plugin.raids().raid(ATT,DEF,System.currentTimeMillis()).isEmpty(),"committed cancellation updates authority even while scheduler is paused");SuiteState.thaw(plugin.getName());finish();});}catch(Exception e){throw new CompletionException(e);}});
    }
    void restartChecks(){await(hp(block(15,160,9)),v->{try{check(Math.abs(v.hp()-100)<.001,"damaged stone HP survives full native restart");check(block(14,160,8).getType()==Material.OAK_DOOR,"replaced door survives full native restart");check(block(15,160,7).getType().isAir(),"destroyed glass is not rolled back on restart");check(block(16,160,8).getType()==Material.CHEST&&((Chest)block(16,160,8).getState()).getBlockInventory().contains(Material.DIAMOND,3),"chest contents survive full native restart");finish();}catch(Exception e){throw new CompletionException(e);}});}
    void finish()throws Exception{
        check(((Map<?,?>)field(engine,"watches")).isEmpty(),"all in-flight block watches released");
        await(store.history(DEF),rows->{try{check(rows.stream().anyMatch(r->r.action().equals("TNT_HIT")),"TNT actor/town audit persisted");TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; native Folia/Towny/TNT/SQLite/TextDisplay; explicit raid Access double, no logged-in players or Army/SiegeWar integration claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}catch(Exception e){throw new CompletionException(e);}});
    }
}
