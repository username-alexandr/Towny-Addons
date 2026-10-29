package ru.neverland.runtime;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.core.*;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.construction.*;
import ru.neverland.townylogistics.*;
import ru.neverland.townylogistics.data.*;
import ru.neverland.townylogistics.model.*;
import ru.neverland.townylogistics.model.Position;
import ru.neverland.townylogistics.model.Network.*;
import ru.neverland.townylogistics.service.*;

/** Native pathfinder and actual Builds shipment journal; no network player or purchased building claimed. */
public final class LogisticsRoutingProbe extends AdmissionsProbe {
    NeverLandTownyLogistics logistics;LogisticsService service;LogisticsRepository repository;CourierNpcs npcs;DataStore buildings;
    final UUID shipment=id("logistics-cargo"),second=id("logistics-return");WanderingTrader npc;Network graph;CourierJob original;Path data;
    @Override void setup()throws Exception{waitReady(80);}
    void waitReady(int remaining)throws Exception{
        logistics=(NeverLandTownyLogistics)Bukkit.getPluginManager().getPlugin("NeverLandTownyLogistics");service=(LogisticsService)field(logistics,"service");
        if(service==null||service.paused()){if(remaining<=0)throw new AssertionError("Logistics did not become ready");later(1,()->waitReady(remaining-1));return;}
        ((BukkitTask)field(service,"task")).cancel();repository=(LogisticsRepository)field(logistics,"repository");npcs=(CourierNpcs)field(service,"npcs");buildings=(DataStore)field(Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds"),"dataStore");data=logistics.getDataFolder().toPath().resolve("logistics-data.yml");mayor=actor(M,"RoutingMayor");
        check(logistics.isEnabled()&&logistics.getDescription().getVersion().equals("0.3.0"),"actual Logistics 0.3.0 asynchronously starts on Folia and Towny");
        if(restart){resumeRouting();return;}
        var u=TownyUniverse.getInstance();u.newTownInternal("RoutingCity",T);town=TownyAPI.getInstance().getTown(T);var resident=u.getDataSource().newResident("RoutingMayor",M);resident.setTown(town);town.setMayor(resident);resident.save();
        for(int x=-1;x<=3;x++)for(int z=-1;z<=3;z++){var claim=new TownBlock(x,z,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(town);if(x==0&&z==0)town.setHomeBlock(claim);claim.save();}town.setSpawn(origin);town.save();
        for(var e:Map.of("warehouse",8,"forestry",24,"bakery",40).entrySet()){var site=new ConstructionSite(e.getKey(),world.getUID(),e.getValue(),120,8,BlockFace.NORTH,1,1,1,false);site.blueprintSet(BlueprintCatalog.currentPin(e.getKey(),BuildingStyle.MEDIEVAL));buildings.town(T).setConstructionSite(site);buildings.town(T).setLevel(e.getKey(),1);}
        buildings.town(T).setCivicInventory("forestry",new ItemStack[]{new ItemStack(Material.STONE,64)});buildings.markDirty();buildings.saveOrThrow();
        var requests=new ArrayList<CompletableFuture<?>>();for(int x=-3;x<=5;x++)for(int z=-3;z<=5;z++){int xx=x,zz=z;var done=new CompletableFuture<Void>();requests.add(done);world.getChunkAtAsync(x,z).whenComplete((chunk,error)->{if(error!=null){done.completeExceptionally(error);return;}Bukkit.getRegionScheduler().run(this,world,xx,zz,task->{try{chunk.addPluginChunkTicket(this);done.complete(null);}catch(Throwable failure){done.completeExceptionally(failure);}});});}
        await(CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)),v->{try{later(10,this::geometry);}catch(Exception error){fail(error);}});
    }
    Position p(double x,double z){return new Position(world.getUID(),x,120,z);}
    void geometry()throws Exception{
        check(Bukkit.isOwnedByCurrentRegion(world,0,0)&&Bukkit.isOwnedByCurrentRegion(world,2,2),"fixture surface belongs to current real Folia region");
        for(int x=0;x<=47;x++)for(int z=0;z<=31;z++){world.getBlockAt(x,119,z).setType(Material.STONE,false);world.getBlockAt(x,120,z).setType(Material.AIR,false);world.getBlockAt(x,121,z).setType(Material.AIR,false);world.getBlockAt(x,122,z).setType(Material.AIR,false);}
        var nodes=Map.of("hub",new Node("hub","warehouse",Kind.BUILDING,p(8.5,8.5)),"source",new Node("source","forestry",Kind.BUILDING,p(24.5,8.5)),"target",new Node("target","bakery",Kind.BUILDING,p(40.5,8.5)),"around1",new Node("around1","",Kind.ROAD,p(24.5,24.5)),"around2",new Node("around2","",Kind.ROAD,p(40.5,24.5)));
        var links=Set.of(new Link("hub","source"),new Link("source","target"),new Link("source","around1"),new Link("around1","around2"),new Link("around2","target"));var route=new Route("wood","hub","source","target","",16,0,false);graph=new Network(T,nodes,links,Map.of("wood",route));
        check(service.builds().depots(T).keySet().containsAll(Set.of("warehouse","forestry","bakery")),"actual Builds API exposes all completed seeded depot sites");
        await(service.change(graph,true),v->{try{startShipment();}catch(Exception error){fail(error);}});
    }
    void startShipment()throws Exception{
        original=new CourierJob(shipment,T,"wood","warehouse","forestry","bakery",1,List.of(p(8.5,8.5),p(24.5,8.5)),List.of(p(24.5,8.5),p(40.5,8.5)),List.of(p(40.5,8.5),p(24.5,8.5),p(8.5,8.5)),CourierJob.Phase.TO_TARGET,1,p(28.5,8.5),0);
        var cargo=service.builds().pickup(T,shipment,"wood","forestry","bakery","",16,0);
        check(cargo!=null&&cargo.transit()&&amount(cargo.cargo())==16&&amount(service.builds().stock(T,"forestry"))==48,"real Builds pickup debits source and records exactly sixteen items once");
        var persist=LogisticsService.class.getDeclaredMethod("persist",Map.class,Map.class);persist.setAccessible(true);
        @SuppressWarnings("unchecked")var saved=(CompletionStage<LogisticsRepository.Snapshot>)persist.invoke(service,Map.of(T,graph),Map.of(shipment,original));await(saved,v->{try{nativeBacktrack();}catch(Exception error){fail(error);}});
    }
    @SuppressWarnings("unchecked")void nativeBacktrack()throws Exception{
        npc=world.spawn(new Location(world,28.5,120,8.5),WanderingTrader.class,n->{n.setPersistent(false);n.setInvulnerable(true);n.setSilent(true);Bukkit.getMobGoals().removeAllGoals(n);n.setCanPickupItems(false);n.setCollidable(false);});
        Class<?> liveType=Class.forName("ru.neverland.townylogistics.service.CourierNpcs$Live",true,logistics.getClass().getClassLoader());var ctor=liveType.getDeclaredConstructors()[0];ctor.setAccessible(true);Object live=ctor.newInstance(npc,original.position(),System.currentTimeMillis());((Map<UUID,Object>)field(npcs,"live")).put(shipment,live);later(5,this::checkNativeBacktrack);
    }
    void checkNativeBacktrack()throws Exception{
        getLogger().info("NPC diagnostic: onGround="+npc.isOnGround()+", ticking="+npc.isTicking()+", position="+npc.getLocation()+", standable="+CourierNpcs.standable(original.position())+", owned="+CourierNpcs.owned(T,original.position()));npcs.beginTick();
        await(npcs.canReturn(original,p(24.5,8.5)),proof->{try{
            getLogger().info("Backtrack probe: "+proof+"; physical distance="+proof.position().distance(original.position()));check(proof.reachable()&&proof.position().distance(original.position())<0.1,"actual native Pathfinder verifies backtrack to last reached node without teleporting NPC");holdFile();pollReroute(80);
        }catch(Exception error){fail(error);}});
    }
    void holdFile()throws Exception{
        var atomic=Class.forName("ru.neverland.core.AtomicFiles",true,logistics.getClass().getClassLoader());var f=atomic.getDeclaredField("writers");f.setAccessible(true);@SuppressWarnings("unchecked")var locks=(Map<Path,ReentrantLock>)f.get(null);var lock=locks.computeIfAbsent(data.toAbsolutePath().normalize(),key->new ReentrantLock());release=new CountDownLatch(1);var acquired=new CountDownLatch(1);var thread=new Thread(()->{lock.lock();acquired.countDown();try{release.await(15,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}finally{lock.unlock();}},"native-logistics-file-delay");thread.setDaemon(true);thread.start();if(!acquired.await(1,TimeUnit.SECONDS))throw new AssertionError("file delay lock failed");
    }
    void pollReroute(int left)throws Exception{
        if(repository.busy()){
            check(service.jobs(T).getFirst().path().equals(original.path())&&repository.state().jobs().get(shipment).detours()==0,"old courier path stays visible while replacement file is physically blocked");
            check(amount(service.builds().shipments(T).get(shipment).cargo())==16&&amount(service.builds().stock(T,"forestry"))==48,"pending reroute neither repeats pickup nor changes cargo ownership");
            check(SuiteState.tryRun("native logistics delayed disk",()->{}),"actual delayed path save leaves SuiteState available");release.countDown();pollSaved(80);return;
        }
        if(left<=0)throw new AssertionError("reroute did not reach durable commit: "+service.status(service.jobs(T).getFirst()));
        npcs.beginTick();var reroute=LogisticsService.class.getDeclaredMethod("tryReroute",CourierJob.class,StopReason.class,long.class);reroute.setAccessible(true);reroute.invoke(service,service.jobs(T).getFirst(),StopReason.BLOCKED,System.currentTimeMillis());later(1,()->pollReroute(left-1));
    }
    void pollSaved(int left)throws Exception{
        if(repository.busy()){if(left<=0)throw new AssertionError("route acknowledgement timed out");later(1,()->pollSaved(left-1));return;}
        var job=service.jobs(T).getFirst();check(job.detours()==1&&job.waypoint()==0&&job.path().equals(List.of(p(24.5,8.5),p(24.5,24.5),p(40.5,24.5),p(40.5,8.5))),"actual service commits alternate path beginning at last reached node");check(job.position().distance(original.position())<0.2&&job.id().equals(shipment)&&job.phase()==CourierJob.Phase.TO_TARGET,"reroute preserves physical location, shipment ID and delivery phase");
        logistics.getCommand("townylogistics").execute(mayor,"townylogistics",new String[]{"info","wood"});check(lore(mayor,22).contains("последней точке")&&windows.get(M).getItem(24).getType()==Material.COMPASS,"native menu explains backtrack and offers one-route recheck");
        denied(()->service.dismiss(T,"wood"),"cargo in transit prevents dismissing its courier");
        await(service.recalculate(T,"wood"),v->{try{var reset=service.jobs(T).getFirst();check(reset.id().equals(shipment)&&reset.path().equals(job.path())&&reset.detours()==0&&amount(service.builds().shipments(T).get(shipment).cargo())==16,"explicit recheck clears blockage history without removing or duplicating cargo");finishRouting();}catch(Exception error){fail(error);}});
    }
    void resumeRouting()throws Exception{
        town=TownyAPI.getInstance().getTown(T);check(town!=null&&service.network(T).routes().containsKey("wood")&&service.jobs(T).size()==1,"actual full JVM restart restores city network and same courier");var job=service.jobs(T).getFirst();
        check(job.id().equals(shipment)&&job.phase()==CourierJob.Phase.TO_TARGET&&job.path().equals(List.of(p(24.5,8.5),p(24.5,24.5),p(40.5,24.5),p(40.5,8.5)))&&job.waypoint()==0,"saved backtrack and replacement leg survive restart exactly");
        check(amount(service.builds().shipments(T).get(shipment).cargo())==16&&amount(service.builds().stock(T,"forestry"))==48&&amount(service.builds().stock(T,"bakery"))==0,"same real transit inventory survives full server restart");
        service.builds().pickup(T,shipment,"wood","forestry","bakery","",16,0);check(amount(service.builds().stock(T,"forestry"))==48,"replayed shipment pickup does not debit source twice");
        check(service.builds().unload(T,shipment,false)&&service.builds().unload(T,shipment,false)&&amount(service.builds().stock(T,"bakery"))==16,"actual unload is idempotent after restart");
        var extra=service.builds().pickup(T,second,"wood","forestry","bakery","",8,0);check(extra!=null&&amount(service.builds().stock(T,"forestry"))==40,"second independent pickup has its own durable shipment");
        await(service.recover(T,second),ok->{try{check(ok&&amount(service.builds().stock(T,"forestry"))==48&&amount(service.builds().stock(T,"bakery"))==16,"administrative return restores exact source inventory and retains delivered items");check(service.builds().shipments(T).get(second).status().equals("RETURNED"),"return receipt remains until acknowledged cleanup");finishRouting();}catch(Exception error){fail(error);}});
    }
    static int amount(ItemStack[] items){return Arrays.stream(items).filter(Objects::nonNull).mapToInt(ItemStack::getAmount).sum();}
    void finishRouting()throws Exception{if(npc!=null&&npc.isValid())npc.remove();TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" checks; actual Folia/Towny/Builds/Logistics, native Pathfinder backtrack, delayed physical file, acknowledged route replacement, exact real shipment inventory, menus and full restart. Explicit offline Player/View/EntityScheduler, seeded completed sites and staged BLOCKED signal; no network client, purchased building or full walking journey claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
