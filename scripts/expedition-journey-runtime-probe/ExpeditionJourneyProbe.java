package ru.neverland.runtime;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.damage.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import com.mojang.authlib.GameProfile;
import ru.neverland.core.*;
import ru.neverland.mintexpeditions.MintTownyExpeditions;
import ru.neverland.mintexpeditions.model.*;
import ru.neverland.mintexpeditions.journey.*;
import ru.neverland.mintexpeditions.service.*;
import ru.neverland.mintexpeditions.gui.*;

/** Real Folia terrain, mobs, inventory/PDC/saveData. Networkless CraftPlayers and fixed site selection are explicit fixtures. */
public final class ExpeditionJourneyProbe extends AdmissionsProbe implements org.bukkit.event.Listener {
    @Override public void onEnable(){super.onEnable();Bukkit.getPluginManager().registerEvents(this,this);}
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR) public void spawned(org.bukkit.event.entity.CreatureSpawnEvent e){getLogger().info("Native spawn "+e.getEntity().getUniqueId()+" "+e.getEntityType()+" cancelled="+e.isCancelled()+" difficulty="+e.getEntity().getWorld().getDifficulty());}
    @org.bukkit.event.EventHandler public void removed(org.bukkit.event.entity.EntityRemoveEvent e){getLogger().info("Native removal "+e.getEntity().getUniqueId()+" "+e.getCause());}
    MintTownyExpeditions expeditionPlugin;ExpeditionService service;ExpeditionRepository repository;JourneyRepository journeys;SiteService sites;ExpeditionMenuManager menu;
    Native leader,idle;UUID scoutId,fightId,legacyId,unknownId;boolean corrupt;final AtomicBoolean permission=new AtomicBoolean(true);final AtomicInteger beats=new AtomicInteger();
    interface Ready{boolean get()throws Exception;}
    void poll(int remaining,Ready ready,Work next)throws Exception{if(ready.get()){next.run();return;}if(remaining<1)throw new AssertionError("Timeout; "+messages);later(1,()->poll(remaining-1,ready,next));}
    final class Native extends CraftPlayer {
        Native(UUID uuid,String name)throws Exception{
            super((CraftServer)Bukkit.getServer(),new ServerPlayer(((CraftServer)Bukkit.getServer()).getServer(),((CraftWorld)world).getHandle(),new GameProfile(uuid,name),ClientInformation.createDefault()));
            var f=net.minecraft.world.entity.Entity.class.getDeclaredField("bukkitEntity");f.setAccessible(true);f.set(getHandleRaw(),this);getHandleRaw().setPosRaw(origin.getX(),origin.getY(),origin.getZ());
            var region=io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData();f=region.getClass().getDeclaredField("allEntities");f.setAccessible(true);((ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity>)f.get(region)).add(getHandleRaw());taskScheduler.registerTo(region.entitySchedulerTickList);
            ((CraftServer)Bukkit.getServer()).getHandle().getPlayersByUUID().put(uuid,(ServerPlayer)getHandleRaw());
        }
        @Override public boolean isOnline(){return true;}@Override public boolean isOp(){return false;}@Override public boolean hasPermission(String p){return permission.get();}@Override public GameMode getGameMode(){return GameMode.SURVIVAL;}
        @Override public void sendMessage(String message){messages.add(getName()+":"+message);}@Override public void sendMessage(String... message){messages.add(getName()+":"+Arrays.toString(message));}@Override public void sendMessage(net.kyori.adventure.text.Component c){messages.add(c.toString());}
        @Override public InventoryView openInventory(Inventory inventory){windows.put(getUniqueId(),inventory);return getOpenInventory();}@Override public InventoryView getOpenInventory(){return view(this,getUniqueId());}@Override public void closeInventory(){windows.remove(getUniqueId());}
        @Override public PlayerInventory getInventory(){if(!Bukkit.isOwnedByCurrentRegion(this))throw new AssertionError("foreign inventory access");return super.getInventory();}
        @Override public void saveData(){if(SuiteState.held())throw new AssertionError("native save under Suite");super.saveData();}
    }
    @Override void setup()throws Exception{
        expeditionPlugin=(MintTownyExpeditions)Bukkit.getPluginManager().getPlugin("NeverLandTownyExpeditions");poll(100,()->expeditionPlugin.service()!=null,()->{
            service=expeditionPlugin.service();repository=service.repository();journeys=(JourneyRepository)field(service,"journeys");sites=(SiteService)field(service,"sites");
            poll(100,()->expeditionPlugin.getCommand("expedition").getExecutor() instanceof ru.neverland.mintexpeditions.command.ExpeditionCommand&&!journeys.busy(),()->{
                corrupt=Files.exists(proof.resolve("corrupt-requested.txt"));expeditionPlugin.getConfig().set("bossbar.enabled",false);service.stopTasks();leader=new Native(M,"JourneyLeader");idle=new Native(S,"JourneyIdle");mayor=leader;sponsor=idle;
                for(var actor:List.of(leader,idle))if(com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(actor.getUniqueId())==null)com.palmergames.bukkit.towny.TownyUniverse.getInstance().getDataSource().newResident(actor.getName(),actor.getUniqueId()).save();
                com.palmergames.bukkit.towny.TownyUniverse.getInstance().getWorld(world.getName()).setUnclaimedZoneDestroy(true);
                menu=new ExpeditionMenuManager(expeditionPlugin,service,new RussianItemNames(expeditionPlugin));check(expeditionPlugin.isEnabled()&&expeditionPlugin.getDescription().getVersion().equals("0.2.0"),"actual Expeditions 0.2.0 enabled on Folia with Towny and Camps");check(Bukkit.isOwnedByCurrentRegion(leader)&&Bukkit.getPlayer(M)==leader,"real offline CraftPlayer participates in native entity ownership and exact player lookup");
                if(corrupt)corruptPhase();else if(restart)restartPhase();else first();
            });
        });
    }
    ExpeditionDefinition definition(){return new ExpeditionDefinition("native_frozen","Сохранённые руины",ExpeditionType.RUINS,ObjectiveType.EXCAVATE,Material.AMETHYST_BLOCK,10,1,3600,1,2,2,Material.AMETHYST_BLOCK,EntityType.ZOMBIE,2,List.of(),List.of(new ItemAmount("minecraft:diamond",new ItemStack(Material.DIAMOND),2)),0,List.of("Только подтверждённый вклад"));}
    ActiveExpedition active(UUID id,boolean planned){var e=new ActiveExpedition(id,M,planned?"native_frozen":"ancient_ruins",world.getUID(),world.getName(),new BlockPos(8,118,8),new BlockPos(8,120,8),System.currentTimeMillis(),System.currentTimeMillis()+3_600_000);e.participants().addAll(Set.of(M,S));e.planned(planned);return e;}
    void create(Journey.Branch branch,java.util.function.Consumer<ActiveExpedition> next)throws Exception{
        var e=active(UUID.randomUUID(),true);var j=new Journey(e.id(),definition(),branch,e.participants(),Map.of(),Map.of(),Journey.State.ACTIVE,"");await(journeys.commit(null,j,M,"created","Native fixed-site fixture with unchanged production conditions"),saved->{await(sites.buildAsync(e,definition()),built->{try{check(built&&e.snapshots().size()>100,"actual site construction waits saved terrain snapshot and creates native blocks and mobs");e.siteReady(true,"");repository.changed();await(repository.saveAsync(),v->next.accept(e));}catch(Exception error){fail(error);}});});
    }
    void first()throws Exception{
        menu.choose(leader,definition());click(leader,13);later(3,()->{
            check(lore(leader,13).contains("Разведка")&&lore(leader,13).contains("Нужен хотя бы один")&&lore(leader,13).contains("2"),"real confirmation menu explains order, frozen rewards and contribution before accepting");
            var old=leader.getOpenInventory();menu.click(new InventoryClickEvent(old,InventoryType.SlotType.CONTAINER,13,ClickType.LEFT,InventoryAction.PICKUP_ALL));windows.put(M,Bukkit.createInventory(null,9));later(3,()->{check(repository.active().isEmpty(),"deferred click from closed confirmation cannot start an expedition");create(Journey.Branch.SCOUT_FIRST,e->{try{scoutId=e.id();roles(e);}catch(Exception error){fail(error);}});});
        });
    }
    void click(Player p,int slot){menu.click(new InventoryClickEvent(p.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL));}
    void roles(ActiveExpedition e)throws Exception{
        check(journeys.get(e.id()).stage()==Journey.Action.OBJECTIVE&&service.pausedMob(e),"scout branch locks combat until all objectives are confirmed");
        getLogger().info("Tracker initialized="+System.getProperties().get("ru.neverland.towny.trackers.v1")+" indexed="+((Map<?,?>)System.getProperties().get("ru.neverland.towny.entities.v1")).keySet()+" listeners="+Arrays.stream(org.bukkit.event.entity.EntitySpawnEvent.getHandlerList().getRegisteredListeners()).map(l->l.getPlugin().getName()+":"+l.getListener().getClass().getName()).toList());
        LivingEntity mob=mob(e);check(WorldTasks.find(mob.getUniqueId())==mob,"owning chunk enumeration resolves native entity index to the exact live mob before production guard lookups");var damage=new EntityDamageByEntityEvent(leader,mob,EntityDamageEvent.DamageCause.ENTITY_ATTACK,DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(leader).withDirectEntity(leader).build(),5);Bukkit.getPluginManager().callEvent(damage);check(damage.isCancelled(),"actual event listener prevents combat during reconnaissance");
        menu.open(leader);check(lore(leader,13).contains("Роль добровольная"),"active menu explains voluntary roles and personal verified contribution");permission.set(false);click(leader,20);later(3,()->{check(!journeys.get(e.id()).roles().containsKey(M),"revoked permission blocks deferred role choice");permission.set(true);menu.open(leader);click(leader,20);poll(100,()->!journeys.busy()&&journeys.get(e.id()).roles().get(M)==Journey.Role.EXPLORER,()->blockedObjective(e));});
    }
    void blockedObjective(ActiveExpedition e)throws Exception{
        BlockPos objective=e.objectives().iterator().next();blockWriter(expeditionPlugin.getDataFolder().toPath().resolve("journeys.yml"));var heartbeat=SuiteScheduler.get().runTaskTimer(this,beats::incrementAndGet,1,1);breakObjective(objective);
        later(4,()->{check(journeys.busy()&&e.completed().isEmpty()&&objective.location(world).getBlock().getType()==Material.AMETHYST_BLOCK,"blocked physical journal write keeps objective block and progress unchanged; busy="+journeys.busy()+" progress="+e.completed().size()+" block="+objective.location(world).getBlock().getType()+" messages="+messages);check(beats.get()>0,"real Suite global heartbeat continues during detached journal write");heartbeat.cancel();release.countDown();poll(100,()->!journeys.busy()&&e.completed().size()==1&&objective.location(world).getBlock().getType().isAir(),()->{breakObjective(objective);check(e.completed().size()==1,"same objective cannot contribute twice");var next=e.objectives().stream().filter(p->!e.completed().contains(p)).findFirst().orElseThrow();breakObjective(next);poll(100,()->!service.journeyUnavailable(e)&&e.completed().size()==2,()->{check(journeys.get(e.id()).stage()==Journey.Action.KILL&&!service.pausedMob(e),"full first stage unlocks actual raid combat only after acknowledgements");kill(e);poll(100,()->!service.journeyUnavailable(e)&&e.kills()==1,()->{kill(e);poll(160,()->repository.get(e.id())==null&&!repository.hasReward(M),this::afterScout);});});});});
    }
    void breakObjective(BlockPos p){var event=new BlockBreakEvent(p.location(world).getBlock(),leader);Bukkit.getPluginManager().callEvent(event);if(!event.isCancelled())throw new AssertionError("objective dropped block");}
    LivingEntity mob(ActiveExpedition e){for(var entity:world.getChunkAt(0,0).getEntities())if(e.spawnedMobs().contains(entity.getUniqueId())&&entity instanceof LivingEntity living&&!living.isDead())return living;throw new AssertionError("native mob missing: expected="+e.spawnedMobs()+" loaded="+Arrays.stream(world.getChunkAt(0,0).getEntities()).map(x->x.getType()+":"+x.getUniqueId()+":dead="+x.isDead()).toList());}
    void kill(ActiveExpedition e){service.observePlayer(leader);var mob=mob(e);mob.setKiller(leader);mob.setHealth(0);}
    int diamonds(){int count=0;for(var i:leader.getInventory().getStorageContents())if(i!=null&&i.getType()==Material.DIAMOND)count+=i.getAmount();return count;}
    void afterScout()throws Exception{
        check(diamonds()==2&&!repository.hasReward(S)&&journeys.get(scoutId).contributed(M)&&!journeys.get(scoutId).contributed(S),"actual saved item reward reaches only contributor; idle participant receives no queued reward");check(repository.history().stream().filter(h->h.id().equals(scoutId)).count()==1,"one completed history record after both stages");
        var marker=leader.getPersistentDataContainer().get(new NamespacedKey(expeditionPlugin,"expedition_reward_receipt"),org.bukkit.persistence.PersistentDataType.STRING);check(marker!=null&&Files.isRegularFile(((CraftServer)Bukkit.getServer()).getHandle().playerIo.getPlayerDir().toPath().resolve(M+".dat")),"native inventory issuance stored operation PDC in actual vanilla player data");
        var legacy=new ActiveExpedition(UUID.randomUUID(),L,"ancient_ruins",world.getUID(),world.getName(),new BlockPos(40,118,8),new BlockPos(40,120,8),System.currentTimeMillis(),System.currentTimeMillis()+3_600_000);legacy.participants().add(L);legacyId=legacy.id();timer(legacy,"pause");legacy.kills(3);repository.put(legacy);repository.reward(id("old-reward"),L,List.of(new ItemStack(Material.GOLD_INGOT,3)),7);await(repository.saveAsync(),v->{try{create(Journey.Branch.FIGHT_FIRST,e->{try{fightId=e.id();var position=e.objectives().iterator().next();breakObjective(position);check(e.completed().isEmpty()&&position.location(world).getBlock().getType()==Material.AMETHYST_BLOCK,"fight-first branch keeps search blocked until all enemies defeated");kill(e);poll(100,()->!service.journeyUnavailable(e)&&e.kills()==1,()->pauseAndUnknown(e));}catch(Exception error){fail(error);}});}catch(Exception error){fail(error);}});
    }
    void pauseAndUnknown(ActiveExpedition e)throws Exception{
        expeditionPlugin.getConfig().set("journeys.enabled",false);var tick=ExpeditionService.class.getDeclaredMethod("tick");tick.setAccessible(true);tick.invoke(service);check(paused(e)&&e.status()==ExpeditionStatus.ACTIVE&&e.kills()==1,"optional mode disable pauses time and progress without failure");check(repository.get(legacyId).kills()==3&&!repository.get(legacyId).planned(),"legacy expedition continues separate old state without new branch migration");expeditionPlugin.getConfig().set("journeys.enabled",true);timer(e,"resume");repository.changed();
        unknownId=id("unknown-native-effect");repository.effects().executeAsync(unknownId,"unknown-native-effect",()->CompletableFuture.failedFuture(new IllegalStateException("explicit unknown external acknowledgement fixture"))).whenComplete((value,error)->later(1,()->{check(error!=null&&effectState(unknownId).equals("PENDING"),"unknown external acknowledgement remains pending for manual review");Files.writeString(proof.resolve("expected.txt"),scoutId+"\n"+fightId+"\n"+legacyId+"\n"+unknownId+"\n"+journeys.get(fightId).contributions().values().iterator().next().at()+"\n");finishJourney();}));
    }
    void restartPhase()throws Exception{
        ids();leader.loadData();check(diamonds()==2&&!repository.hasReward(M),"full JVM restart restores exact acknowledged native reward without reissue");var e=repository.get(fightId);check(e!=null&&e.planned()&&e.kills()==1&&journeys.get(fightId).kills()==1&&journeys.get(fightId).stage()==Journey.Action.KILL,"restart restores unfinished second branch and its exact completed first kill");check(service.registry().get("native_frozen")==null&&service.definition(e).goal()==2&&service.definition(e).rewards().getFirst().amount()==2,"frozen active conditions survive absent current template");check(journeys.get(fightId).contributions().values().iterator().next().at()==Long.parseLong(Files.readAllLines(proof.resolve("expected.txt")).get(4)),"restart preserves historical contribution timestamp");check(repository.get(legacyId).kills()==3&&!repository.get(legacyId).planned()&&repository.rewards(L).getFirst().money()==7,"old active progress and pending legacy reward survive asynchronous migration");check(effectState(unknownId).equals("PENDING"),"restart retains unknown payment without automatic replay");
        poll(100,()->!e.spawnedMobs().isEmpty(),()->{kill(e);poll(100,()->!service.journeyUnavailable(e)&&e.kills()==2,()->{check(journeys.get(e.id()).stage()==Journey.Action.OBJECTIVE,"second branch opens search only after remaining native enemy death");var positions=new ArrayList<>(e.objectives());breakObjective(positions.get(0));poll(100,()->!service.journeyUnavailable(e)&&e.completed().size()==1,()->{breakObjective(positions.get(1));poll(160,()->repository.get(fightId)==null&&!repository.hasReward(M),()->{check(diamonds()==4&&!repository.hasReward(S),"resumed branch gives exactly one frozen reward to verified contributor");finishJourney();});});});});
    }
    void timer(ActiveExpedition e,String action)throws Exception{Object timer=ActiveExpedition.class.getMethod("timer").invoke(e);Object updated=timer.getClass().getMethod("edit",String.class,long.class,long.class).invoke(timer,action,0L,System.currentTimeMillis());ActiveExpedition.class.getMethod("timer",timer.getClass()).invoke(e,updated);}
    boolean paused(ActiveExpedition e)throws Exception{Object timer=ActiveExpedition.class.getMethod("timer").invoke(e);return (boolean)timer.getClass().getMethod("paused").invoke(timer);}
    String effectState(UUID id)throws Exception{return repository.effects().getClass().getMethod("state",UUID.class).invoke(repository.effects(),id).toString();}
    void ids()throws Exception{var lines=Files.readAllLines(proof.resolve("expected.txt"));scoutId=UUID.fromString(lines.get(0));fightId=UUID.fromString(lines.get(1));legacyId=UUID.fromString(lines.get(2));unknownId=UUID.fromString(lines.get(3));}
    void corruptPhase()throws Exception{ids();check(!journeys.ready()&&repository.writable()&&expeditionPlugin.isEnabled(),"corrupt optional journal does not disable core expedition storage or plugin");check(repository.get(legacyId).kills()==3&&!repository.get(legacyId).planned(),"legacy expedition remains readable during optional journal failure");check(Files.readString(expeditionPlugin.getDataFolder().toPath().resolve("journeys.yml")).equals("schema: 999\n"),"corrupt optional source is preserved unchanged for recovery");leader.loadData();check(diamonds()==4&&effectState(unknownId).equals("PENDING"),"optional failure neither reissues acknowledged rewards nor retries unknown effect");finishJourney();}
    void blockWriter(Path path)throws Exception{var type=Class.forName("ru.neverland.core.AtomicFiles",true,field(journeys,"data").getClass().getClassLoader());var f=type.getDeclaredField("writers");f.setAccessible(true);@SuppressWarnings("unchecked")var locks=(Map<Path,ReentrantLock>)f.get(null);var lock=locks.computeIfAbsent(path.toAbsolutePath(),p->new ReentrantLock());var acquired=new CountDownLatch(1);release=new CountDownLatch(1);var holder=new Thread(()->{lock.lock();acquired.countDown();try{release.await(12,TimeUnit.SECONDS);}catch(Exception ignored){}finally{lock.unlock();}});holder.setDaemon(true);holder.start();if(!acquired.await(1,TimeUnit.SECONDS))throw new AssertionError("writer lock unavailable");}
    void finishJourney()throws Exception{
        await(repository.saveIfDirtyAsync(),v->{try{Files.writeString(proof.resolve((corrupt?"corrupt":restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; real Folia/Towny/Camps/Expeditions, generated native terrain and enemies, live events, detached journal with global heartbeat, frozen order and role, native inventory/PDC/saveData, legacy migration and full restart. Fixed site and networkless registered CraftPlayer actors are explicit fixtures; no real client login, camp resource debit or economy provider payment certified.\n");((CraftServer)Bukkit.getServer()).getHandle().getPlayersByUUID().remove(M);((CraftServer)Bukkit.getServer()).getHandle().getPlayersByUUID().remove(S);Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}catch(Exception error){fail(error);}});
    }
}
