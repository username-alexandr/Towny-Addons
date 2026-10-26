package ru.neverland.townylogistics.service;
import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townylogistics.config.LogisticsSettings;
import ru.neverland.townylogistics.model.*;
import java.util.*;
import java.util.function.Consumer;

/** Native NPC movement. Never force-load chunks or teleport a courier along its route. */
public final class CourierNpcs implements Listener {
    public record Motion(Position position,boolean active,String status){}
    private static final Set<Material> ROAD=Set.of(Material.DIRT_PATH,Material.STONE_BRICKS,Material.COBBLESTONE,Material.POLISHED_ANDESITE,Material.SMOOTH_STONE);
    private static final Set<Material> DANGER=Set.of(Material.LAVA,Material.WATER,Material.FIRE,Material.SOUL_FIRE,Material.CACTUS,Material.MAGMA_BLOCK,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.POWDER_SNOW);
    private static final class Live {final WanderingTrader npc;Position last,safe;long movedAt,repathAt,retryAt;Live(WanderingTrader n,Position p,long now){npc=n;last=p;safe=p;movedAt=now;}}
    private final JavaPlugin plugin;private final NamespacedKey key;private final Map<UUID,Live> live=new HashMap<>();private LogisticsSettings settings;private int budget;
    private final Map<UUID,Motion> motions=new HashMap<>();
    private final Map<UUID,Request> requests=new HashMap<>();
    private final Set<UUID> pending=new HashSet<>();
    private record Request(CourierJob job,Position target,boolean moving,int level){}
    private final Consumer<Map.Entry<UUID,Position>> checkpoint;
    public CourierNpcs(JavaPlugin plugin,LogisticsSettings settings,Consumer<Map.Entry<UUID,Position>> checkpoint){this.plugin=plugin;this.key=new NamespacedKey(plugin,"courier");this.settings=settings;this.checkpoint=checkpoint;}
    public void start(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {ru.neverland.core.WorldTasks.eachEntity(plugin,e->{if(ours(e))e.remove();});Bukkit.getPluginManager().registerEvents(this,plugin);} }
    public void settings(LogisticsSettings value){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {settings=value;for(UUID id:List.copyOf(live.keySet()))if(live.size()>settings.active())remove(id);} }
    public void beginTick(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {budget=settings.pathBudget();} }
    public int count(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return live.size();} }
    public static Position position(Location l){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {return new Position(l.getWorld().getUID(),l.getX(),l.getY(),l.getZ());} }
    public static Location location(Position p){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {World w=Bukkit.getWorld(p.world());return w==null?null:new Location(w,p.x(),p.y(),p.z());} }
    public static boolean loaded(Position p){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {World w=Bukkit.getWorld(p.world());return w!=null&&w.isChunkLoaded(Math.floorDiv((int)Math.floor(p.x()),16),Math.floorDiv((int)Math.floor(p.z()),16));} }
    public static boolean owned(UUID town,Position p){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {var l=location(p);if(l==null)return false;var t=TownyAPI.getInstance().getTown(l);return t!=null&&town.equals(t.getUUID());} }
    public static boolean standable(Position p){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {if(!loaded(p))return false;var l=location(p);if(!ru.neverland.core.WorldTasks.ownsBox(l,1))return false;var feet=l.getBlock();var head=feet.getRelative(0,1,0);var ground=feet.getRelative(0,-1,0);
        boolean feetClear=feet.isPassable()||feet.getBoundingBox().getMaxY()<=p.y()+0.03;
        var support=feet.getType().isSolid()&&!feet.isPassable()?feet:ground;
        return feetClear&&head.isPassable()&&!DANGER.contains(feet.getType())&&!DANGER.contains(support.getType())&&support.getType().isSolid();} }
    public boolean near(Position point){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {World w=Bukkit.getWorld(point.world());if(w==null)return false;double r=settings.radius();return Bukkit.getOnlinePlayers().stream().map(ru.neverland.core.WorldTasks::position).filter(Objects::nonNull).anyMatch(p->position(p).distance(point)<=r);} }
    private boolean searchLoaded(Position a,Position z){if(!a.world().equals(z.world()))return false;World w=Bukkit.getWorld(a.world());if(w==null)return false;
        int minX=Math.floorDiv((int)Math.floor(Math.min(a.x(),z.x())-48),16),maxX=Math.floorDiv((int)Math.floor(Math.max(a.x(),z.x())+48),16);
        int minZ=Math.floorDiv((int)Math.floor(Math.min(a.z(),z.z())-48),16),maxZ=Math.floorDiv((int)Math.floor(Math.max(a.z(),z.z())+48),16);
        if(maxX-minX>12||maxZ-minZ>12)return false;
        for(int x=minX;x<=maxX;x++)for(int zz=minZ;zz<=maxZ;zz++)if(!w.isChunkLoaded(x,zz)||!Bukkit.isOwnedByCurrentRegion(w,x,zz))return false;return true;
    }
    private Live spawn(CourierJob job,long now){
        if(live.size()>=settings.active()||!near(job.position())||!standable(job.position())||!owned(job.town(),job.position()))return null;
        var point=location(job.position());WanderingTrader npc=point.getWorld().spawn(point,WanderingTrader.class,n->{
            n.getPersistentDataContainer().set(key,PersistentDataType.STRING,job.id().toString());n.setPersistent(false);n.setRemoveWhenFarAway(false);n.setInvulnerable(true);n.setSilent(true);n.setCollidable(false);n.setCanPickupItems(false);
            n.setAdult();n.setAgeLock(true);n.setDespawnDelay(Integer.MAX_VALUE);n.setCanDrinkMilk(false);n.setCanDrinkPotion(false);n.setRecipes(List.of());
            n.customName(net.kyori.adventure.text.Component.text("Курьер • "+job.route()));n.setCustomNameVisible(true);
            n.getEquipment().setItemInMainHand(new ItemStack(Material.CHEST));n.getEquipment().setItemInMainHandDropChance(0);n.getEquipment().setItemInOffHandDropChance(0);
            Bukkit.getMobGoals().removeAllGoals(n);n.getPathfinder().setCanFloat(false);n.getPathfinder().setCanOpenDoors(false);n.getPathfinder().setCanPassDoors(true);
            var range=n.getAttribute(org.bukkit.attribute.Attribute.FOLLOW_RANGE);if(range!=null)range.setBaseValue(32);
        });
        if(!npc.isValid()){npc.remove();return null;}Live value=new Live(npc,job.position(),now);live.put(job.id(),value);return value;
    }
    public Motion tick(CourierJob job,Position target,boolean moving,int level) { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {
        requests.put(job.id(),new Request(job,target,moving,level));
        if(pending.add(job.id())) {
            Runnable advance=()->{try{Request r=requests.get(job.id());if(r!=null)motions.put(job.id(),move(r.job(),r.target(),r.moving(),r.level()));}finally{pending.remove(job.id());}};
            Live liveNpc=live.get(job.id());Location at=location(job.position());
            if(liveNpc!=null)ru.neverland.core.SuiteScheduler.entityLater(plugin,liveNpc.npc,advance,()->{pending.remove(job.id());live.remove(job.id());},1);
            else if(at!=null)ru.neverland.core.SuiteScheduler.region(plugin,at,advance);else pending.remove(job.id());
        }
        return motions.getOrDefault(job.id(),new Motion(job.position(),false,"Ожидает регион курьера"));
    }} }
    private Motion move(CourierJob job,Position target,boolean moving,int level){ try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {
        long now=System.currentTimeMillis();Live value=live.get(job.id());
        if(value!=null&&!value.npc.isValid()){live.remove(job.id());value=null;}
        if(!near(job.position())||!loaded(job.position())){remove(job.id());return new Motion(job.position(),false,"Ожидает загрузки территории и игроков");}
        if(value==null)value=spawn(job,now);if(value==null)return new Motion(job.position(),false,"Ожидает свободного места для NPC или прохода");
        var npc=value.npc;Position current=position(npc.getLocation());if(npc.isOnGround()&&standable(current))value.safe=current;Position safePosition=value.safe;
        if(!owned(job.town(),current)){remove(job.id());return new Motion(job.position(),false,"Курьер оказался вне городской территории");}
        if(!npc.isTicking())return new Motion(safePosition,false,"Чанк не обрабатывает сущности");
        if(!moving){npc.getPathfinder().stopPathfinding();value.movedAt=now;value.last=current;return new Motion(safePosition,true,"");}
        if(current.distance(target)<=1.2){npc.getPathfinder().stopPathfinding();value.movedAt=now;return new Motion(safePosition,true,"");}
        if(!loaded(target)||!owned(job.town(),target)){npc.getPathfinder().stopPathfinding();return new Motion(safePosition,false,"Следующий участок недоступен");}
        if(current.distance(value.last)>0.2){value.last=current;value.movedAt=now;}
        if(now-value.movedAt>=settings.stuck()*1000L){npc.getPathfinder().stopPathfinding();value.retryAt=now+settings.retry()*1000L;value.movedAt=now;}
        if(now<value.retryAt)return new Motion(safePosition,false,"Путь перекрыт; ожидает повторной попытки");
        if(now>=value.repathAt&&budget>0){
            if(!searchLoaded(current,target)){npc.getPathfinder().stopPathfinding();return new Motion(safePosition,false,"Ожидает загрузки пути");}
            budget--;value.repathAt=now+2000;var destination=location(target);destination.setY(Math.ceil(destination.getY()));var path=npc.getPathfinder().findPath(destination,0);
            boolean valid=path!=null&&path.canReachFinalPoint()&&NavigationPolicy.allowed(path.getPoints().stream().map(CourierNpcs::position).toList(),target,
                point->loaded(point)&&ru.neverland.core.SuiteScheduler.owns(location(point))&&owned(job.town(),point)&&!DANGER.contains(location(point).getBlock().getType())&&!DANGER.contains(location(point).getBlock().getRelative(0,-1,0).getType()));
            if(!valid){npc.getPathfinder().stopPathfinding();value.retryAt=now+settings.retry()*1000L;return new Motion(safePosition,false,"Нет безопасного пути; откройте проход или дверь");}
            double speed=ru.neverland.integration.ResearchEffects.speed(settings.level(level).speed(),ru.neverland.integration.ResearchBonuses.bonus(job.town(),"fast_caravans"))*(1+ru.neverland.integration.SpecializationAccess.bonus(job.town(),"courier_speed"));Material ground=npc.getLocation().getBlock().getRelative(0,-1,0).getType();Material feet=npc.getLocation().getBlock().getType();
            if(Tag.RAILS.isTagged(feet)||Tag.RAILS.isTagged(ground))speed*=1+settings.railBonus();else if(ROAD.contains(ground)||ROAD.contains(feet))speed*=1+settings.roadBonus();
            npc.getPathfinder().moveTo(path,speed);
        }
        return new Motion(safePosition,true,"");
    } }
    public void remove(UUID id){ try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {requests.remove(id);motions.remove(id);Live v=live.remove(id);if(v!=null){checkpoint.accept(Map.entry(id,v.safe));ru.neverland.core.WorldTasks.remove(plugin,v.npc);} }}
    public void stop(){ try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {for(UUID id:List.copyOf(live.keySet()))remove(id);} }
    private boolean ours(Entity e){return e!=null&&e.getPersistentDataContainer().has(key,PersistentDataType.STRING);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void damage(EntityDamageEvent e){ try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyLogistics")){ru.neverland.core.ModuleGate.stopEvent(e);return;}if(ours(e.getEntity()))e.setCancelled(true);} }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void interact(PlayerInteractEntityEvent e){ try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyLogistics")){ru.neverland.core.ModuleGate.stopEvent(e);return;}if(ours(e.getRightClicked()))e.setCancelled(true);} }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void leash(PlayerLeashEntityEvent e){ try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyLogistics")){ru.neverland.core.ModuleGate.stopEvent(e);return;}if(ours(e.getEntity()))e.setCancelled(true);} }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void pickup(EntityPickupItemEvent e){ try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyLogistics")){ru.neverland.core.ModuleGate.stopEvent(e);return;}if(ours(e.getEntity()))e.setCancelled(true);} }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void target(EntityTargetLivingEntityEvent e){ try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyLogistics")){ru.neverland.core.ModuleGate.stopEvent(e);return;}if(ours(e.getTarget())||ours(e.getEntity()))e.setCancelled(true);} }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void teleport(EntityTeleportEvent e){ try (var suiteGuard21 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyLogistics")){ru.neverland.core.ModuleGate.stopEvent(e);return;}if(ours(e.getEntity()))e.setCancelled(true);} }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void portal(EntityPortalEvent e){ try (var suiteGuard22 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyLogistics")){ru.neverland.core.ModuleGate.stopEvent(e);return;}if(ours(e.getEntity()))e.setCancelled(true);} }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void transform(EntityTransformEvent e){ try (var suiteGuard23 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyLogistics")){ru.neverland.core.ModuleGate.stopEvent(e);return;}if(ours(e.getEntity()))e.setCancelled(true);} }
    @EventHandler public void death(EntityDeathEvent e){ try (var suiteGuard24 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyLogistics")){ru.neverland.core.ModuleGate.stopEvent(e);return;}if(ours(e.getEntity())){e.getDrops().clear();e.setDroppedExp(0);}} }
    @EventHandler public void load(EntitiesLoadEvent e){
        if(ru.neverland.core.SuiteState.frozen("NeverLandTownyLogistics"))return;
        // Removal of stale, nonpersistent NPCs touches only entities owned by this region.
        for(Entity entity:e.getEntities())if(ours(entity))entity.remove();
    }
    @EventHandler public void unload(EntitiesUnloadEvent e){
        if(ru.neverland.core.SuiteState.frozen("NeverLandTownyLogistics"))return;
        for(Entity entity:e.getEntities())if(ours(entity)){
            try{
                UUID id=UUID.fromString(entity.getPersistentDataContainer().get(key,PersistentDataType.STRING));
                ru.neverland.core.SuiteScheduler.get().runTask(plugin,()->{
                    Live current=live.get(id);
                    // A later spawn of the same delivery must not be removed by stale cleanup.
                    if(current!=null&&current.npc==entity)remove(id);
                });
            }catch(IllegalArgumentException ignored){}
            entity.remove();
        }
    }
}
