package ru.neverland.townysieges;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.*;

/** HP applies only after final explosion cancellation, on the owning block regions. */
public final class TntRaidEngine implements Listener,AutoCloseable {
    public interface Access {
        boolean ready(); boolean managed(UUID town); boolean valid(RaidController.Permit permit);
        void authorize(UUID actor,UUID defender,Consumer<RaidController.Permit> permitted);
    }
    private record Origin(UUID world,double x,double y,double z){}
    private record Ticket(UUID actor,long at){}
    private record Watch(long epoch,int references){}
    private final NeverLandTownySiegesPlus plugin;
    private final RaidController raids;
    private final RaidStore store;
    private final NamespacedKey actorKey;
    private Access access;
    private final Map<String,UUID> owners=new ConcurrentHashMap<>();
    private final Map<String,Ticket> priming=new ConcurrentHashMap<>();
    private final Map<UUID,Long> processed=new ConcurrentHashMap<>();
    private final Map<RaidStore.Key,Watch> watches=new ConcurrentHashMap<>();
    private final Set<String> damaged=ConcurrentHashMap.newKeySet();
    private final AtomicLong epochs=new AtomicLong();
    private volatile boolean closed,loaded;
    public TntRaidEngine(NeverLandTownySiegesPlus plugin,RaidController raids,SiegeService siege) {
        this.plugin=plugin;this.raids=raids;store=raids.store();actorKey=new NamespacedKey(plugin,"raid_tnt_actor");
        access=new Access(){
            public boolean ready(){return raids.ready();}
            public boolean managed(UUID town){return raids.managed(town);}
            public boolean valid(RaidController.Permit permit){return raids.valid(permit);}
            public void authorize(UUID id,UUID town,Consumer<RaidController.Permit> permitted){
                Player player=Bukkit.getPlayer(id);if(player==null)return;
                SuiteScheduler.player(plugin,player,p->{try{permitted.accept(raids.authorize(p,town));}catch(Exception|LinkageError denied){siege.tell(p,RaidController.problem(denied));}});
            }
        };
        store.ready().thenCompose(v->store.owners()).thenCompose(values->{owners.putAll(values);return store.damagedKeys();})
                .whenComplete((values,error)->{if(error==null&&!closed){damaged.addAll(values);loaded=true;}});
    }
    private boolean active(){return !closed&&loaded&&plugin.isEnabled()&&!SuiteState.frozen(plugin.getName())&&access.ready();}
    private static RaidStore.Key key(Block b){return new RaidStore.Key(b.getWorld().getUID(),b.getX(),b.getY(),b.getZ());}
    private static Block canonical(Block b){return b.getBlockData() instanceof Door door&&door.getHalf()==Bisected.Half.TOP?b.getRelative(0,-1,0):b;}
    private void changed(Block block) {
        var k=key(canonical(block));watches.computeIfPresent(k,(unused,w)->new Watch(epochs.incrementAndGet(),w.references()));
        boolean owned=owners.remove(key(block).id())!=null;
        boolean dirty=damaged.remove(k.id())||watches.containsKey(k);
        if(store.healthy()){if(owned)store.owner(key(block),null);if(dirty)store.reset(k);}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void place(BlockPlaceEvent e){try(var guard=SuiteState.guard()){
        if(closed||!loaded)return;
        changed(e.getBlock());
        if(raids.settings().enabled()&&e.getBlock().getType()==Material.TNT&&e.getPlayer().getGameMode()==GameMode.SURVIVAL&&owners.size()<20000) {
            var k=key(e.getBlock());owners.put(k.id(),e.getPlayer().getUniqueId());store.owner(k,e.getPlayer().getUniqueId());
        }
        if(e instanceof BlockMultiPlaceEvent multi)for(var state:multi.getReplacedBlockStates())changed(state.getBlock());
    }}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void broken(BlockBreakEvent e){try(var guard=SuiteState.guard()){if(!closed&&loaded)changed(e.getBlock());}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void piston(BlockPistonExtendEvent e){try(var guard=SuiteState.guard()){if(!closed&&loaded)for(Block block:e.getBlocks()){changed(block);changed(block.getRelative(e.getDirection()));}}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void retract(BlockPistonRetractEvent e){try(var guard=SuiteState.guard()){if(!closed&&loaded)for(Block block:e.getBlocks()){changed(block);changed(block.getRelative(e.getDirection()));}}}
    private UUID owner(Entity entity) {
        if(entity instanceof Player player)return player.getUniqueId();
        if(entity instanceof Projectile projectile&&projectile.getShooter() instanceof Player player)return player.getUniqueId();
        if(entity instanceof TNTPrimed tnt) {
            String text=tnt.getPersistentDataContainer().get(actorKey,PersistentDataType.STRING);
            if(text!=null)try{return UUID.fromString(text);}catch(IllegalArgumentException ignored){}
            var source=tnt.getSource();if(source instanceof Player player)return player.getUniqueId();
        }
        return null;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void prime(TNTPrimeEvent e){try(var guard=SuiteState.guard()){
        if(!active())return;
        UUID id=owner(e.getPrimingEntity());var k=key(e.getBlock());if(id==null)id=owners.get(k.id());
        if(id!=null)priming.put(k.id(),new Ticket(id,System.currentTimeMillis()));
        if(owners.remove(k.id())!=null)store.owner(k,null);
        priming.entrySet().removeIf(entry->System.currentTimeMillis()-entry.getValue().at()>3000);
    }}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void spawn(EntitySpawnEvent e){try(var guard=SuiteState.guard()){
        if(!active()||!(e.getEntity() instanceof TNTPrimed tnt))return;
        UUID id=owner(tnt);var at=tnt.getLocation();var ticket=priming.remove(new RaidStore.Key(at.getWorld().getUID(),at.getBlockX(),at.getBlockY(),at.getBlockZ()).id());
        if(id==null&&ticket!=null&&System.currentTimeMillis()-ticket.at()<3000)id=ticket.actor();
        if(id!=null)tnt.getPersistentDataContainer().set(actorKey,PersistentDataType.STRING,id.toString());
    }}
    private Set<UUID> towns(Origin at) {
        World world=Bukkit.getWorld(at.world());if(world==null)return Set.of();int radius=(int)Math.ceil(raids.settings().radius());var result=new HashSet<UUID>();
        // Towny's configured claim size need not match Minecraft's chunk size.
        for(int x=(int)Math.floor(at.x())-radius;x<=(int)Math.floor(at.x())+radius;x++)
            for(int z=(int)Math.floor(at.z())-radius;z<=(int)Math.floor(at.z())+radius;z++) {
                var town=TownyAPI.getInstance().getTown(new Location(world,x+.5,at.y(),z+.5));
                if(town!=null&&access.managed(town.getUUID()))result.add(town.getUUID());
            }
        return Set.copyOf(result);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void protect(EntityExplodeEvent e){try(var guard=SuiteState.guard()){
        if(closed||!plugin.isEnabled()||!raids.settings().enabled()||!(e.getEntity() instanceof TNTPrimed))return;
        // Keep native TNT from bypassing HP. Never re-add blocks removed by Towny or un-cancel an event.
        e.blockList().removeIf(block->{var town=TownyAPI.getInstance().getTown(block.getLocation());return town!=null&&access.managed(town.getUUID());});
    }}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void exploded(EntityExplodeEvent e){try(var guard=SuiteState.guard()){
        if(!active()||!(e.getEntity() instanceof TNTPrimed tnt))return;
        UUID actor=owner(tnt);if(actor==null)return;
        long now=System.currentTimeMillis();processed.entrySet().removeIf(entry->now-entry.getValue()>60000);
        if(processed.size()>4000||processed.putIfAbsent(tnt.getUniqueId(),now)!=null)return;
        var at=e.getLocation();var origin=new Origin(at.getWorld().getUID(),at.getX(),at.getY(),at.getZ());
        UUID blast=tnt.getUniqueId();for(UUID town:towns(origin))access.authorize(actor,town,permit->queue(origin,blast,permit));
    }}
    private void queue(Origin origin,UUID blast,RaidController.Permit permit) {
        if(!active()||!access.valid(permit))return;World world=Bukkit.getWorld(origin.world());if(world==null)return;
        int radius=(int)Math.ceil(raids.settings().radius());
        for(int x=(((int)Math.floor(origin.x())-radius)>>4);x<=(((int)Math.floor(origin.x())+radius)>>4);x++)
            for(int z=(((int)Math.floor(origin.z())-radius)>>4);z<=(((int)Math.floor(origin.z())+radius)>>4);z++) {
                int cx=x,cz=z;
                SuiteScheduler.region(plugin,new Location(world,cx*16,origin.y(),cz*16),()->capture(world,cx,cz,origin,blast,permit));
            }
    }
    private long watch(RaidStore.Key k){return watches.compute(k,(unused,w)->w==null?new Watch(epochs.incrementAndGet(),1):new Watch(w.epoch(),w.references()+1)).epoch();}
    private boolean unchanged(RaidStore.Hit hit){var w=watches.get(hit.key());return w!=null&&w.epoch()==hit.generation();}
    private void release(RaidStore.Hit hit){watches.computeIfPresent(hit.key(),(k,w)->w.references()<=1?null:new Watch(w.epoch(),w.references()-1));}
    private void capture(World world,int cx,int cz,Origin origin,UUID blast,RaidController.Permit permit) {
        if(!active()||!access.valid(permit)||!world.isChunkLoaded(cx,cz))return;
        var settings=raids.settings();int radius=(int)Math.ceil(settings.radius());var hits=new ArrayList<RaidStore.Hit>();var seen=new HashSet<RaidStore.Key>();
        int minX=Math.max(cx*16,(int)Math.floor(origin.x())-radius),maxX=Math.min(cx*16+15,(int)Math.floor(origin.x())+radius);
        int minZ=Math.max(cz*16,(int)Math.floor(origin.z())-radius),maxZ=Math.min(cz*16+15,(int)Math.floor(origin.z())+radius);
        int minY=Math.max(world.getMinHeight(),(int)Math.floor(origin.y())-radius),maxY=Math.min(world.getMaxHeight()-1,(int)Math.floor(origin.y())+radius);
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
            var town=TownyAPI.getInstance().getTown(new Location(world,x,minY,z));if(town==null||!town.getUUID().equals(permit.defender()))continue;
            for(int y=minY;y<=maxY;y++) {
            Block raw=world.getBlockAt(x,y,z);double amount=TntRules.damage(TntRules.distance(origin.x(),origin.y(),origin.z(),x,y,z),settings);if(amount<=0)continue;
            Block block=canonical(raw);var k=key(block);if(!seen.add(k))continue;
            Material material=block.getType();boolean core=material==Material.LECTERN&&plugin.loot().core(town.getUUID(),block);
            if(material.isAir()||(!material.isSolid()&&!(block.getBlockData() instanceof Door)&&!core)||TntRules.protectedMaterial(material.name()))continue;
            var state=block.getState(false);if((state instanceof TileState||state instanceof InventoryHolder)&&!core)continue;
            // A door is one object. Its nearest half receives one hit, never two hits from one blast.
            if(block.getBlockData() instanceof Door)amount=Math.max(amount,TntRules.damage(TntRules.distance(origin.x(),origin.y(),origin.z(),block.getX(),block.getY()+1,block.getZ()),settings));
            damaged.add(k.id());hits.add(new RaidStore.Hit(k,town.getUUID(),material.name(),watch(k),settings.hp(material.name()),amount));
            }
        }
        if(hits.isEmpty())return;
        store.damage(blast,permit.actor().toString(),permit.attacker(),permit.defender(),System.currentTimeMillis(),settings.repairSeconds(),List.copyOf(hits))
                .whenComplete((results,error)->{
                    if(closed)return;
                    SuiteScheduler.region(plugin,new Location(world,cx*16,origin.y(),cz*16),()->{
                        try {
                            if(error!=null){plugin.getLogger().warning("Урон TNT отложен: "+RaidController.problem(error));return;}
                            for(var result:results)if(!result.receipt().isEmpty()) {
                                if(result.hit().material().equals("LECTERN")){breach(world,cx,cz,origin,result,permit);continue;}
                                boolean destroyed=false;
                                try{if(active()&&access.valid(permit)&&world.isChunkLoaded(cx,cz)&&unchanged(result.hit())) {
                                    var k=result.hit().key();Block block=world.getBlockAt(k.x(),k.y(),k.z());var town=TownyAPI.getInstance().getTown(block.getLocation());
                                    if(town!=null&&town.getUUID().equals(permit.defender())&&block.getType().name().equals(result.hit().material())&&!(block.getState(false) instanceof TileState)) {
                                        if(block.getBlockData() instanceof Door){Block top=block.getRelative(0,1,0);if(top.getType()==block.getType())top.setType(Material.AIR,false);}
                                        block.setType(Material.AIR,false);destroyed=true;damaged.remove(k.id());
                                    }
                                }}finally{store.finish(result,destroyed);}
                            }
                        }finally{hits.forEach(this::release);}
                    });
                });
    }
    private byte[] book(Block block){var item=((org.bukkit.block.Lectern)block.getState(false)).getInventory().getItem(0);return item==null||item.getType().isAir()?new byte[0]:item.serializeAsBytes();}
    private void breach(World world,int cx,int cz,Origin origin,RaidStore.Result result,RaidController.Permit permit){
        var hit=result.hit();var k=hit.key();Block block=world.getBlockAt(k.x(),k.y(),k.z());
        if(!active()||!access.valid(permit)||!unchanged(hit)||!plugin.loot().core(permit.defender(),block)){store.finish(result,false);return;}
        byte[] book;
        try{book=book(block);}catch(RuntimeException error){store.finish(result,false);return;}
        watch(k);plugin.loot().store().arm(permit,k,book,plugin.loot().settings()).whenComplete((loot,error)->{
            if(closed){release(hit);return;}
            if(error!=null||!active()){store.finish(result,false);if(error==null&&loot.state().equals("ARMED"))plugin.loot().store().change(loot.id(),"ARMED","VOID","module paused before core destruction");release(hit);return;}
            SuiteScheduler.region(plugin,new Location(world,cx*16,origin.y(),cz*16),()->{
                boolean destroyed=false;
                try{
                    if(!loot.state().equals("ARMED")||!active()||!access.valid(permit)||!world.isChunkLoaded(cx,cz)||!unchanged(hit))return;
                    Block current=world.getBlockAt(k.x(),k.y(),k.z());var town=TownyAPI.getInstance().getTown(current.getLocation());
                    if(town==null||!town.getUUID().equals(permit.defender())||!plugin.loot().core(town.getUUID(),current)||!Arrays.equals(book,book(current)))return;
                    current.setType(Material.AIR,false);damaged.remove(k.id());destroyed=true;
                }finally{
                    store.finish(result,destroyed);release(hit);
                    if(loot.state().equals("ARMED"))plugin.loot().store().change(loot.id(),"ARMED",destroyed?"BREACHED":"VOID",destroyed?"authorized TNT destroyed registered town hall core":"world changed before core destruction");
                }
            });
        });
    }
    public void health(Player player) {
        if(!active())throw new IllegalArgumentException("Рейды сейчас недоступны");
        if(!WorldTasks.ownsBox(player.getLocation(),6))throw new IllegalArgumentException("Подойдите ближе к блоку");
        Block raw=player.getTargetBlockExact(6);if(raw==null)throw new IllegalArgumentException("Посмотрите на дверь или блок");
        Block block=canonical(raw);var town=TownyAPI.getInstance().getTown(block.getLocation());if(town==null)throw new IllegalArgumentException("Этот блок находится за пределами города");
        var type=block.getType();var k=key(block);
        if((TntRules.protectedMaterial(type.name())||block.getState(false) instanceof TileState||block.getState(false) instanceof InventoryHolder)&&!plugin.loot().core(town.getUUID(),block)){raids.reply(player,"Этот блок защищён от боевого TNT");return;}
        var settings=raids.settings();store.health(k,type.name(),settings.hp(type.name()),System.currentTimeMillis(),settings.repairSeconds())
                .whenComplete((value,error)->{if(error!=null){raids.reply(player,RaidController.problem(error));return;}raids.reply(player,"Прочность блока: "+(long)Math.ceil(value.hp())+" / "+(long)Math.ceil(value.maximum())+" HP. После затишья повреждённый целый блок восстановит прочность.");});
    }
    @Override public void close(){closed=true;owners.clear();priming.clear();processed.clear();watches.clear();damaged.clear();}
}
