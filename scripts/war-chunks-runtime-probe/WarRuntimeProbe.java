package ru.neverland.runtime;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.craftbukkit.*;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import net.minecraft.server.level.*;
import com.mojang.authlib.GameProfile;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.event.executors.TownyActionEventExecutor;
import ru.neverland.core.*;
import ru.neverland.townysieges.*;
import ru.neverland.townysieges.api.TownyWarChunkApi;
import ru.neverland.townybuilds.api.BuildingRepairPlansApi;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.construction.*;

/** Real Folia ownership, Towny listeners/decisions, SQLite and TNT; explicit Army policy and offline actors. */
public final class WarRuntimeProbe extends TntRuntimeProbe {
    static final UUID WAR=id("war-049"),VISITOR=id("visitor-049");
    volatile List<RaidStore.Raid> declarations=List.of();boolean policy=true,army=true,foreignBuild,foreignDamage;
    WarControl war;FixturePlayer raider,guard,visitor;DataStore data;BuildingRepairPlansApi plans;Block firstPart,lastPart;Location empty,outer;
    @Override void setup()throws Exception{
        plugin=(NeverLandTownySiegesPlus)Bukkit.getPluginManager().getPlugin("NeverLandTownySiegesPlus");engine=plugin.tnt();store=plugin.raids().store();
        check(plugin.isEnabled()&&store.healthy(),"SiegesPlus 0.5.0 and SQLite loaded on real Folia");
        check(Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds").getDescription().getVersion().equals("0.8.43"),"Builds 0.8.43 loaded");
        var u=TownyUniverse.getInstance();if(!restart){u.newTownInternal("WarDefenders",DEF);u.newTownInternal("WarAttackers",ATT);
            defender=TownyAPI.getInstance().getTown(DEF);attacker=TownyAPI.getInstance().getTown(ATT);
            for(var entry:Map.of(ACTOR,"WarRaider",MAYOR,"WarMayor",VISITOR,"WarVisitor").entrySet()){
                var r=u.getDataSource().newResident(entry.getValue(),entry.getKey());if(!entry.getKey().equals(VISITOR))r.setTown(entry.getKey().equals(ACTOR)?attacker:defender);r.save();
            }
            defender.setMayor(TownyAPI.getInstance().getResident(MAYOR));attacker.setMayor(TownyAPI.getInstance().getResident(ACTOR));
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){var c=new TownBlock(x,z,u.getWorld(world.getName()));u.addTownBlock(c);c.setTown(defender);if(x==0&&z==0)defender.setHomeBlock(c);c.save();}
            var c=new TownBlock(3,0,u.getWorld(world.getName()));u.addTownBlock(c);c.setTown(attacker);attacker.setHomeBlock(c);c.save();
            defender.setSpawn(origin);attacker.setSpawn(new Location(world,50.5,160,8.5));defender.setNeutral(false);attacker.setNeutral(false);
            attacker.setRegistered(System.currentTimeMillis()-172800000L);defender.setRegistered(System.currentTimeMillis()-172800000L);defender.save();attacker.save();
        }else{defender=TownyAPI.getInstance().getTown(DEF);attacker=TownyAPI.getInstance().getTown(ATT);}
        defender.setPVP(false);attacker.setPVP(false);var rules=u.getWorld(world.getName());rules.setPVP(false);rules.setForcePVP(false);rules.save();
        for(int x=-2;x<=3;x++)for(int z=-2;z<=2;z++)world.getChunkAt(x,z).addPluginChunkTicket(this);
        check(!plugin.raids().healthy(),"production controller fails closed without real Army provider");
        var owner=Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds");data=(DataStore)field(owner,"dataStore");plans=Bukkit.getServicesManager().load(BuildingRepairPlansApi.class);
        empty=new Location(world,-8.5,160,-8.5);outer=new Location(world,48.5,160.5,24.5);
        raider=new FixturePlayer(ACTOR,"WarRaider",empty);guard=new FixturePlayer(MAYOR,"WarMayor",empty);visitor=new FixturePlayer(VISITOR,"WarVisitor",empty);
        check(Bukkit.isOwnedByCurrentRegion(raider)&&Bukkit.isOwnedByCurrentRegion(guard),"offline CraftPlayer handles obey actual Folia region ownership");
        var previous=plugin.warControl();previous.close();Bukkit.getServicesManager().unregister(TownyWarChunkApi.class,previous);for(var r:HandlerList.getRegisteredListeners(plugin))if(r.getListener() instanceof WarTownyListener)HandlerList.unregisterAll(r.getListener());
        war=new WarControl(plugin,plugin.raids(),new WarSettings(true,5),new WarControl.Access(){
            public List<RaidStore.Raid> declarations(){return policy?declarations:List.of();}
            public List<RaidStore.Raid> live(){return declarations;}
            public boolean battle(RaidStore.Raid w){return policy&&w.fighting(System.currentTimeMillis());}
            public boolean mobilized(UUID id){return army&&id.equals(ACTOR);}
            public boolean valid(RaidController.Permit permit){return policy&&declarations.stream().anyMatch(w->w.id().equals(permit.raid())&&w.fighting(System.currentTimeMillis()));}
        });set(plugin,"warControl",war);Bukkit.getPluginManager().registerEvents(new WarTownyListener(plugin,war),plugin);
        Bukkit.getServicesManager().register(TownyWarChunkApi.class,war,plugin,ServicePriority.Normal);
        set(engine,"access",new TntRaidEngine.Access(){public boolean ready(){return store.healthy();}public boolean managed(UUID town){return town.equals(DEF);}public boolean valid(RaidController.Permit p){return policy&&p.raid().equals(WAR);}public void authorize(UUID id,UUID town,Consumer<RaidController.Permit> allowed){if(policy&&id.equals(ACTOR)&&town.equals(DEF))allowed.accept(new RaidController.Permit(ACTOR,ATT,DEF,WAR,System.currentTimeMillis(),false));}});
        if(restart){await(store.raids(),rows->{try{declarations=rows.stream().filter(r->r.id().equals(WAR)&&r.live(System.currentTimeMillis())).toList();check(declarations.size()==1,"same active war recovered from SQLite");restartChecks();}catch(Exception e){throw new CompletionException(e);}});return;}
        long now=System.currentTimeMillis();var w=new RaidStore.Raid(WAR,ATT,DEF,ACTOR,now,now+4000,now+600000,now+86400000,"OPEN");
        await(store.start(w),rows->{try{declarations=rows;warmup();}catch(Exception e){throw new CompletionException(e);}});
    }
    final class FixturePlayer extends CraftPlayer {
        boolean active=true;GameMode mode=GameMode.SURVIVAL;Inventory top=Bukkit.createInventory(null,9);final List<String> messages=new ArrayList<>();
        FixturePlayer(UUID id,String name,Location at)throws Exception{
            super((CraftServer)Bukkit.getServer(),new ServerPlayer(((CraftServer)Bukkit.getServer()).getServer(),((CraftWorld)world).getHandle(),new GameProfile(id,name),ClientInformation.createDefault()));
            var f=net.minecraft.world.entity.Entity.class.getDeclaredField("bukkitEntity");f.setAccessible(true);f.set(getHandleRaw(),this);at(at);
            // Offline actors are in the owning region identity list only, not login/player/ticking lists.
            var region=io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData();
            var list=region.getClass().getDeclaredField("allEntities");list.setAccessible(true);
            ((ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity>)list.get(region)).add(getHandleRaw());
        }
        void at(Location at){getHandleRaw().setPosRaw(at.getX(),at.getY(),at.getZ());}
        @Override public boolean isOnline(){return active;}
        @Override public boolean isOp(){return false;}
        @Override public boolean hasPermission(String permission){return permission.startsWith("neverland")||permission.equals("towny.command.town");}
        @Override public GameMode getGameMode(){return mode;}
        @Override public void sendMessage(String message){messages.add(message);}
        @Override public void sendMessage(String... messages){this.messages.addAll(List.of(messages));}
        @Override public void sendMessage(UUID source,String message){messages.add(message);}
        @Override public void sendMessage(net.kyori.adventure.text.Component message){messages.add(message.toString());}
        @Override public InventoryView openInventory(Inventory inventory){top=inventory;return null;}
        @Override public void closeInventory(){}
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void foreign(EntityDamageByEntityEvent e){if(foreignDamage)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void foreign(BlockPlaceEvent e){if(foreignBuild)e.setCancelled(true);}
    EntityDamageByEntityEvent hit(Entity from,Player to,EntityDamageEvent.DamageCause cause){
        var type=cause==EntityDamageEvent.DamageCause.ENTITY_EXPLOSION?org.bukkit.damage.DamageType.EXPLOSION:org.bukkit.damage.DamageType.PLAYER_ATTACK;
        var source=org.bukkit.damage.DamageSource.builder(type).withCausingEntity(from).withDirectEntity(from).build();var event=new EntityDamageByEntityEvent(from,to,cause,source,6);Bukkit.getPluginManager().callEvent(event);return event;
    }
    boolean prime(Location at,TNTPrimeEvent.PrimeCause cause,Entity entity){Block b=at.getBlock();b.setType(Material.TNT,false);var e=new TNTPrimeEvent(b,cause,entity,null);Bukkit.getPluginManager().callEvent(e);b.setType(Material.AIR,false);return !e.isCancelled();}
    boolean canPrime(Location at)throws Exception{return (Boolean)call(war,"primeAllowed",new Class<?>[]{Location.class},at);}
    void warmup()throws Exception{
        var tb=TownyAPI.getInstance().getTownBlock(empty);tb.getPermissions().pvp=false;tb.getPermissions().explosion=false;
        policy=false;check(hit(raider,guard,EntityDamageEvent.DamageCause.ENTITY_ATTACK).isCancelled(),"baseline Towny pipeline denies PvP without war policy");policy=true;
        check(!hit(raider,guard,EntityDamageEvent.DamageCause.ENTITY_ATTACK).isCancelled(),"Towny's real damage pipeline permits enemy melee during declaration warmup");
        check(!hit(guard,raider,EntityDamageEvent.DamageCause.ENTITY_ATTACK).isCancelled(),"defenders can fight invaders in their city");
        check(hit(raider,visitor,EntityDamageEvent.DamageCause.ENTITY_ATTACK).isCancelled(),"war does not expose a townless visitor to PvP");
        foreignDamage=true;check(hit(raider,guard,EntityDamageEvent.DamageCause.ENTITY_ATTACK).isCancelled(),"foreign Bukkit damage cancellation retained");foreignDamage=false;
        check(!prime(empty,TNTPrimeEvent.PrimeCause.REDSTONE,null),"redstone cannot prime TNT in uncaptured city during warmup");
        check(!tb.getPermissions().pvp&&!tb.getPermissions().explosion,"Towny plot PvP and explosion flags were not changed");
        later(100,()->battle());
    }
    void battle()throws Exception{
        check(declarations.getFirst().fighting(System.currentTimeMillis()),"warmup expires before capture tests");
        check(!TownyActionEventExecutor.canBuild(raider,empty,Material.TNT),"Towny refuses attacker TNT placement before capture");
        for(var cause:TNTPrimeEvent.PrimeCause.values())check(!prime(empty,cause,null),"uncaptured city blocks TNT priming cause "+cause);
        check(prime(outer,TNTPrimeEvent.PrimeCause.REDSTONE,null),"priming outside city remains allowed");
        Block dispenser=block(-10,160,-9);dispenser.setType(Material.DISPENSER,false);var face=(org.bukkit.block.data.Directional)dispenser.getBlockData();face.setFacing(BlockFace.EAST);dispenser.setBlockData(face,false);
        var dispensed=new BlockDispenseEvent(dispenser,new ItemStack(Material.TNT),new org.bukkit.util.Vector());Bukkit.getPluginManager().callEvent(dispensed);check(dispensed.isCancelled(),"dispenser TNT blocked before item consumption");
        dispenser.setType(Material.DROPPER,false);var dropped=new BlockDispenseEvent(dispenser,new ItemStack(Material.TNT),new org.bukkit.util.Vector());Bukkit.getPluginManager().callEvent(dropped);check(!dropped.isCancelled(),"dropper can still drop an unlit TNT item");dispenser.setType(Material.AIR,false);
        var deniedSpawn=world.spawn(empty,TNTPrimed.class);check(deniedSpawn==null||!deniedSpawn.isValid(),"direct TNT spawn cannot bypass uncaptured city guard");
        var arrow=world.spawn(empty,Arrow.class);arrow.setShooter(raider);check(!hit(arrow,guard,EntityDamageEvent.DamageCause.PROJECTILE).isCancelled(),"real arrow resolves enemy shooter through Towny");arrow.remove();
        var bomb=world.spawn(outer,TNTPrimed.class);bomb.setFuseTicks(10000);bomb.getPersistentDataContainer().set(new NamespacedKey(plugin,"raid_tnt_actor"),org.bukkit.persistence.PersistentDataType.STRING,ACTOR.toString());
        check(!hit(bomb,guard,EntityDamageEvent.DamageCause.ENTITY_EXPLOSION).isCancelled(),"attributed TNT damages enemy through both Towny explosion and PvP checks");
        check(hit(bomb,visitor,EntityDamageEvent.DamageCause.ENTITY_EXPLOSION).isCancelled(),"attributed TNT cannot exploit war to hurt townless visitor");bomb.remove();
        TownyAPI.getInstance().getResident(MAYOR).addRespawnProtection(200);check(hit(raider,guard,EntityDamageEvent.DamageCause.ENTITY_ATTACK).isCancelled(),"Towny respawn shield is retained during war");TownyAPI.getInstance().getResident(MAYOR).removeRespawnProtection();
        guard.at(new Location(world,8.5,160,8.5));seedBuildings();
        raider.at(empty);samples();later(65,()->contest());
    }
    void samples(){war.sample(raider);war.sample(guard);}
    void tickSamples(int left,Work then){samples();war.pulse();if(left<=0){try{then.run();}catch(Exception e){fail(e);}return;}later(10,()->tickSamples(left-1,then));}
    void contest()throws Exception{
        // Refreshing snapshots is explicit because fixture actors never log in.
        tickSamples(4,()->{guard.at(empty);samples();war.pulse();later(25,()->{check(!canPrime(empty),"defender contests empty chunk before capture");guard.at(new Location(world,8.5,160,8.5));war.sample(guard);raider.mode=GameMode.SPECTATOR;war.sample(raider);war.pulse();later(10,()->{check(!canPrime(empty),"spectator cannot occupy a chunk");raider.mode=GameMode.SURVIVAL;raider.at(empty);tickSamples(13,()->occupied());});});});
    }
    void occupied()throws Exception{
        check(canPrime(empty),"empty chunk becomes usable only after observed occupation");
        check(TownyActionEventExecutor.canBuild(raider,empty,Material.TNT),"Towny permits narrow TNT placement after capture");
        check(!TownyActionEventExecutor.canBuild(raider,empty,Material.DIAMOND_BLOCK),"capture grants no general building permission");
        check(prime(empty,TNTPrimeEvent.PrimeCause.REDSTONE,null),"redstone primes TNT in captured chunk");
        check(!TownyActionEventExecutor.canBuild(visitor,empty,Material.TNT),"captured chunk does not grant TNT permission to visitors");
        check(!canPrime(new Location(world,8.5,160,-8.5)),"adjacent uncaptured chunk remains locked");
        army=false;war.sample(raider);check(!TownyActionEventExecutor.canBuild(raider,empty,Material.TNT),"demobilization removes attacker's TNT placement exception");army=true;war.sample(raider);
        policy=false;check(!war.canPlaceTnt(raider,empty),"temporary camp unavailability suspends capture permission");war.pulse();policy=true;
        check(canPrime(empty),"camp availability recovery restores the same saved capture");
        fire(4,()->partialBuilding());
    }
    @SuppressWarnings("unchecked") void seedBuildings()throws Exception{
        var city=data.town(DEF);city.setLevel("warehouse",1);city.setConstructionSite(new ConstructionSite("warehouse",world.getUID(),47,159,24,BlockFace.NORTH,1,1,1,false));
        city.setLevel("sawmill",1);city.setConstructionSite(new ConstructionSite("sawmill",world.getUID(),46,159,24,BlockFace.NORTH,1,1,1,false));
        city.setConstructionSite(new ConstructionSite("town_hall",world.getUID(),-20,159,32,BlockFace.NORTH,0,1,1,true));
        var all=plans.warPlans(DEF);check(all.stream().anyMatch(p->p.get("project").equals("town_hall")),"war geometry includes unfinished first-level construction");
        check(plans.plan(DEF,"town_hall").isEmpty(),"unfinished construction remains ineligible for completed-building repairs");
        int targetZ=24;
        for(var plan:all)if(!plan.get("project").equals("town_hall")){
            String project=(String)plan.get("project");var cell=((List<Map<String,Object>>)plan.get("cells")).getFirst();var old=city.constructionSite(project);
            city.setConstructionSite(new ConstructionSite(project,world.getUID(),old.originX()+47-((Number)cell.get("x")).intValue(),old.originY()+160-((Number)cell.get("y")).intValue(),old.originZ()+targetZ-((Number)cell.get("z")).intValue(),BlockFace.NORTH,1,1,1,false));targetZ++;
        }
        firstPart=block(47,160,24);lastPart=block(47,160,25);firstPart.setType(Material.STONE,false);lastPart.setType(Material.OBSIDIAN,false);data.markDirty();data.saveOrThrow();war.reload(new WarSettings(true,5));
        check(!canPrime(firstPart.getLocation()),"chunk containing two registered buildings begins locked");
    }
    void fire(int left,Work then)throws Exception{
        if(left==0){later(25,then);return;}var t=world.spawn(outer,TNTPrimed.class);t.setGravity(false);t.setFuseTicks(2);t.getPersistentDataContainer().set(new NamespacedKey(plugin,"raid_tnt_actor"),org.bukkit.persistence.PersistentDataType.STRING,ACTOR.toString());later(15,()->fire(left-1,then));
    }
    void partialBuilding()throws Exception{
        check(firstPart.getType().isAir(),"real TNT launched outside city destroys the first registered part through HP");
        check(lastPart.getType()==Material.OBSIDIAN&&!canPrime(lastPart.getLocation()),"another building part in the chunk prevents early capture");
        // Finish the remaining registered part with confirmed authorized HP-engine hits.
        lastPart.setType(Material.GLASS,false);engine.broken(new BlockBreakEvent(lastPart,raider));fire(1,()->buildingTaken());
    }
    void buildingTaken()throws Exception{
        check(lastPart.getType().isAir(),"last building part physically destroyed");war.pulse();later(35,()->{
            check(canPrime(lastPart.getLocation()),"chunk with demolished building parts is captured without empty-plot delay");
            var log=new ArrayList<AuditRecord>();AuditJournal.scan(plugin.getDataFolder().toPath().resolve("audit"),log::add);
            check(log.stream().anyMatch(r->r.kind().equals("RAID_CHUNK")&&r.outcome().equals("CAPTURED")),"shared admin journal contains capture with actor and coordinates");
            check(log.stream().anyMatch(r->r.kind().equals("RAID_TNT_DENIED")),"blocked TNT ignition is logged");
            raider.at(lastPart.getLocation().clone().add(.5,0,.5));war.sample(raider);lastPart.setType(Material.TNT,false);
            var placed=new BlockPlaceEvent(lastPart,lastPart.getState(),lastPart.getRelative(BlockFace.DOWN),new ItemStack(Material.TNT),raider,true,org.bukkit.inventory.EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(placed);
            check(!placed.isCancelled(),"construction site and Towny both allow TNT in captured building footprint");
            var removed=new BlockBreakEvent(lastPart,raider);Bukkit.getPluginManager().callEvent(removed);check(!removed.isCancelled(),"attacker can recover unlit TNT from captured building footprint");lastPart.setType(Material.AIR,false);
            lastPart.setType(Material.TNT,false);foreignBuild=true;var protectedPlace=new BlockPlaceEvent(lastPart,lastPart.getState(),lastPart.getRelative(BlockFace.DOWN),new ItemStack(Material.TNT),raider,true,org.bukkit.inventory.EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(protectedPlace);check(protectedPlace.isCancelled(),"foreign block protection is retained after capture");foreignBuild=false;lastPart.setType(Material.AIR,false);
            var menu=field(Bukkit.getPluginManager().getPlugin("NeverLandTownyControl"),"adminMenu");call(menu,"raids",new Class<?>[]{Player.class},raider);
            check(raider.top.getItem(38).getType()==Material.FILLED_MAP&&raider.top.getItem(42).getType()==Material.TNT,"admin menu separates chunk captures from rejected TNT ignition");
            finish();
        });
    }
    @Override void restartChecks(){
        later(30,()->{check(canPrime(empty),"empty chunk capture survives full Folia restart for the same war");
            var captureAt=new Location(world,47,160,24);check(canPrime(captureAt),"demolished building chunk capture survives full restart");
            check(!canPrime(new Location(world,8,160,-8)),"uncaptured neighbor remains locked after restart");
            var records=new ArrayList<AuditRecord>();AuditJournal.scan(plugin.getDataFolder().toPath().resolve("audit"),records::add);check(records.stream().filter(r->r.kind().equals("RAID_CHUNK")&&r.outcome().equals("CAPTURED")).count()==2,"restart keeps exactly one admin receipt per captured chunk");
            var old=declarations;declarations=List.of(new RaidStore.Raid(UUID.randomUUID(),ATT,DEF,ACTOR,System.currentTimeMillis(),0,System.currentTimeMillis()+60000,0,"OPEN"));
            check(!canPrime(empty),"new war identity cannot inherit captured chunks");declarations=old;
            await(store.cancel(DEF,"native admin",System.currentTimeMillis()),rows->{try{declarations=rows.stream().filter(r->r.live(System.currentTimeMillis())).toList();
                check(!war.canPlaceTnt(raider,empty),"cancellation immediately removes placement exception");
                check(hit(raider,guard,EntityDamageEvent.DamageCause.ENTITY_ATTACK).isCancelled(),"Towny peaceful PvP applies again after cancellation");
                war.pulse();later(30,()->{var log=new ArrayList<AuditRecord>();AuditJournal.scan(plugin.getDataFolder().toPath().resolve("audit"),log::add);check(log.stream().anyMatch(r->r.kind().equals("RAID_CHUNK")&&r.outcome().equals("RELEASED")),"war ending durably logs release of temporary chunks");finish();});
            }catch(Exception e){throw new CompletionException(e);}});
        });
    }
    @Override void finish()throws Exception{
        check(((Map<?,?>)field(engine,"watches")).isEmpty(),"all HP callbacks released Folia block watches");data.markDirty();data.saveOrThrow();TownyUniverse.getInstance().getDataSource().saveAll();
        Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; genuine Folia/Towny/TNT/SQLite, offline CraftPlayer actors and explicit Army/camp policy; no real-client Army session claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());
    }
}
