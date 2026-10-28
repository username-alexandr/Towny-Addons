package ru.neverland.townysieges;

import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.*;

/** Temporary military control. Towny ownership and permanent plot flags are never changed. */
public final class WarControl implements AutoCloseable,ru.neverland.townysieges.api.TownyWarChunkApi {
    public interface Access {
        List<RaidStore.Raid> declarations(); boolean battle(RaidStore.Raid war); boolean mobilized(UUID actor);
        default boolean valid(RaidController.Permit permit){return false;}
        default List<RaidStore.Raid> live(){return declarations();}
    }
    private record Sample(UUID actor,UUID town,UUID world,int x,int y,int z,boolean soldier,long at){
        boolean fresh(long now){return now>=at&&now-at<=2500;}
        boolean at(WarCaptureStore.Key k){return world.equals(k.world())&&(x>>4)==k.x()&&(z>>4)==k.z();}
    }
    private record Geometry(long at,Map<String,List<RaidStore.Key>> chunks){}
    private record Breach(UUID actor,long at){}
    private final NeverLandTownySiegesPlus plugin;
    private final RaidController raids;
    private final WarCaptureStore store;
    private final Access access;
    private volatile WarSettings settings;
    private final Map<UUID,Sample> players=new ConcurrentHashMap<>();
    private final Map<WarCaptureStore.Key,WarCaptureStore.Capture> captures=new ConcurrentHashMap<>();
    private final Map<WarCaptureStore.Key,ChunkHold.State> holds=new ConcurrentHashMap<>();
    private final Map<WarCaptureStore.Key,Breach> breaches=new ConcurrentHashMap<>();
    private final Map<UUID,Geometry> geometry=new ConcurrentHashMap<>();
    private final Map<Material,Boolean> tiles=new ConcurrentHashMap<>();
    private final Set<WarCaptureStore.Key> pending=ConcurrentHashMap.newKeySet(),releasing=ConcurrentHashMap.newKeySet();
    private final Map<String,Long> notices=new ConcurrentHashMap<>();
    private volatile boolean loaded,closed;
    private final java.util.concurrent.atomic.AtomicBoolean journalling=new java.util.concurrent.atomic.AtomicBoolean();
    private int cursor;
    public WarControl(NeverLandTownySiegesPlus plugin,RaidController raids,WarSettings settings){this(plugin,raids,settings,new Access(){
        public List<RaidStore.Raid> declarations(){return raids.declarations();}
        public boolean battle(RaidStore.Raid war){return raids.battle(war);}
        public boolean valid(RaidController.Permit permit){return raids.valid(permit);}
        public List<RaidStore.Raid> live(){return raids.openWars();}
        public boolean mobilized(UUID actor){try{return raids.mobilized(actor);}catch(Exception|LinkageError unavailable){return false;}}
    });}
    /** Explicit policy adapter for the opt-in disposable native fixture. */
    public WarControl(NeverLandTownySiegesPlus plugin,RaidController raids,WarSettings settings,Access access){
        this.plugin=plugin;this.raids=raids;this.settings=settings;this.access=access;store=new WarCaptureStore(raids.store());
        raids.store().ready().thenCompose(v->store.load()).whenComplete((rows,error)->{if(error==null&&!closed){for(var c:rows)captures.putIfAbsent(c.key(),c);loaded=true;}});
    }
    public void reload(WarSettings next){settings=next;holds.clear();geometry.clear();}
    boolean configured(){return !closed&&settings.enabled();}
    private boolean enabled(){return !closed&&loaded&&settings.enabled()&&plugin.isEnabled()&&raids.ready()&&!SuiteState.frozen(plugin.getName());}
    List<RaidStore.Raid> wars(){return enabled()?access.declarations():List.of();}
    RaidStore.Raid war(UUID town){return wars().stream().filter(w->w.defender().equals(town)&&w.live(System.currentTimeMillis())).findFirst().orElse(null);}
    private RaidStore.Raid war(WarCaptureStore.Key k){return wars().stream().filter(w->w.id().equals(k.raid())&&w.live(System.currentTimeMillis())).findFirst().orElse(null);}
    private static WarCaptureStore.Key key(RaidStore.Raid war,Location at){return new WarCaptureStore.Key(war.id(),at.getWorld().getUID(),at.getBlockX()>>4,at.getBlockZ()>>4);}
    private static UUID town(Location at){var t=at==null?null:TownyAPI.getInstance().getTown(at);return t==null?null:t.getUUID();}
    public void sample(Player player){if(!configured()||access.live().isEmpty()){players.remove(player.getUniqueId());return;}if(SuiteScheduler.owns(player))sample(player,player.getLocation());}
    void sample(Player player,Location at){
        if(!configured()||access.live().isEmpty()){players.remove(player.getUniqueId());return;}
        if(closed||!SuiteScheduler.owns(player))return;UUID id=player.getUniqueId();
        if(!player.isOnline()||player.isDead()||player.getGameMode()!=GameMode.SURVIVAL){players.remove(id);return;}
        var own=SiegeService.town(id);if(own==null){players.remove(id);return;}
        players.put(id,new Sample(id,own.getUUID(),at.getWorld().getUID(),at.getBlockX(),at.getBlockY(),at.getBlockZ(),player.hasPermission("neverlandtownysiegesplus.raid.use")&&access.mobilized(id),System.currentTimeMillis()));
    }
    public void forget(UUID actor){players.remove(actor);}
    /** Metadata-only war pairing; Towny's respawn/jail/admin protections remain authoritative. */
    boolean pvp(UUID attacker,UUID victim,Location at){
        UUID territory=town(at);if(territory==null)return false;var w=war(territory);if(w==null)return false;
        var a=SiegeService.town(attacker);var d=SiegeService.town(victim);if(a==null||d==null)return false;
        return a.getUUID().equals(w.attacker())&&d.getUUID().equals(w.defender())||a.getUUID().equals(w.defender())&&d.getUUID().equals(w.attacker());
    }
    boolean captured(RaidStore.Raid war,Location at){return enabled()&&access.battle(war)&&captures.containsKey(key(war,at))&&war.defender().equals(town(at));}
    /** Applies to every priming cause, including chains, redstone and dispenser projectiles. */
    boolean primeAllowed(Location at){var w=war(town(at));return w==null||captured(w,at);}
    boolean attackerTnt(Player player,Location at){
        if(!enabled())return false;var w=war(town(at));if(w==null||!captured(w,at))return false;
        if(SuiteScheduler.owns(player))sample(player);var s=players.get(player.getUniqueId());var own=SiegeService.town(player.getUniqueId());return s!=null&&s.fresh(System.currentTimeMillis())&&s.soldier()&&s.town().equals(w.attacker())&&own!=null&&own.getUUID().equals(w.attacker())&&access.mobilized(player.getUniqueId());
    }
    @Override public boolean canPlaceTnt(Player actor,Location location){try(var guard=SuiteState.guard()){return actor!=null&&location!=null&&location.getWorld()!=null&&attackerTnt(actor,location);}}
    public void pulse(){
        if(closed)return;
        if(!enabled()){holds.clear();breaches.clear();return;}
        journal();
        long now=System.currentTimeMillis();players.entrySet().removeIf(e->!e.getValue().fresh(now));notices.entrySet().removeIf(e->now-e.getValue()>60000);
        var wars=wars();var live=new HashMap<UUID,RaidStore.Raid>();for(var w:wars)live.put(w.id(),w);
        // Cancellation, camp loss and expiry revoke permission immediately in captured(); persistence follows.
        var open=new HashSet<UUID>();for(var w:access.live())if(w.live(now))open.add(w.id());
        // No Army polling or player work in peaceful time. Live entities stay on entity schedulers.
        if(!open.isEmpty())SuiteScheduler.players(plugin,this::sample);else players.clear();
        for(var c:captures.values())if(!open.contains(c.key().raid())&&releasing.add(c.key())){
            store.release(c,now).whenComplete((changed,error)->{releasing.remove(c.key());if(error==null)captures.remove(c.key(),c);});
        }
        holds.keySet().removeIf(k->!live.containsKey(k.raid()));breaches.keySet().removeIf(k->!live.containsKey(k.raid()));
        var work=new LinkedHashSet<WarCaptureStore.Key>();work.addAll(holds.keySet());work.addAll(breaches.keySet());
        for(var s:players.values())if(s.soldier())for(var w:wars)if(w.attacker().equals(s.town())&&access.battle(w)){
            World world=Bukkit.getWorld(s.world());if(world!=null&&w.defender().equals(town(new Location(world,s.x(),s.y(),s.z()))))work.add(new WarCaptureStore.Key(w.id(),s.world(),s.x()>>4,s.z()>>4));
        }
        var list=new ArrayList<>(work);list.sort(Comparator.comparing(Object::toString));int size=list.size();
        for(int i=0;i<Math.min(32,size);i++){var k=list.get((cursor+i)%size);var w=live.get(k.raid());World world=Bukkit.getWorld(k.world());if(w!=null&&world!=null&&!captures.containsKey(k))SuiteScheduler.region(plugin,new Location(world,k.x()*16,0,k.z()*16),()->advance(w,k,world));}
        if(size>0)cursor=(cursor+32)%size;
    }
    @SuppressWarnings("unchecked") private List<RaidStore.Key> cells(UUID town,WarCaptureStore.Key key)throws Exception{
        long now=System.currentTimeMillis();var prior=geometry.get(town);
        if(prior==null||now-prior.at()>3000){
            var api=ApiServices.require("NeverLandTownyBuilds","ru.neverland.townybuilds.api.BuildingRepairPlansApi","warPlans");
            var out=new HashMap<String,List<RaidStore.Key>>();int count=0;
            for(var plan:(Collection<Map<String,Object>>)api.invoke("warPlans",new Class<?>[]{UUID.class},town))for(var cell:(Collection<Map<String,Object>>)plan.get("cells")){
                if(++count>1_000_000)throw new IllegalStateException("Слишком много блоков в планах зданий");
                String material=(String)cell.get("material");if(TntRules.protectedMaterial(material))continue;
                var k=new RaidStore.Key((UUID)plan.get("world"),((Number)cell.get("x")).intValue(),((Number)cell.get("y")).intValue(),((Number)cell.get("z")).intValue());
                out.computeIfAbsent(chunk(k.world(),k.x()>>4,k.z()>>4),unused->new ArrayList<>()).add(k);
            }
            var immutable=new HashMap<String,List<RaidStore.Key>>();out.forEach((k,v)->immutable.put(k,List.copyOf(new LinkedHashSet<>(v))));
            prior=new Geometry(now,Map.copyOf(immutable));geometry.put(town,prior);
        }
        return prior.chunks().getOrDefault(chunk(key.world(),key.x(),key.z()),List.of());
    }
    private static String chunk(UUID world,int x,int z){return world+":"+x+":"+z;}
    private boolean standing(World world,UUID defender,List<RaidStore.Key> cells){
        for(var k:cells){if(k.y()<world.getMinHeight()||k.y()>=world.getMaxHeight())continue;Block b=world.getBlockAt(k.x(),k.y(),k.z());
            if(!defender.equals(town(b.getLocation())))continue;var m=b.getType();
            if(m.isAir()||TntRules.protectedMaterial(m.name()))continue;
            // Do not make protected inventories or decorations an impossible capture objective.
            if(m!=Material.LECTERN&&tiles.computeIfAbsent(m,type->type.createBlockData().createBlockState() instanceof TileState))continue;
            if(m.isSolid()||b.getBlockData() instanceof Door||m==Material.LECTERN)return true;
        }return false;
    }
    private void advance(RaidStore.Raid w,WarCaptureStore.Key k,World world){
        if(!enabled()||war(k)==null||!access.battle(w)||!world.isChunkLoaded(k.x(),k.z())||captures.containsKey(k)||pending.contains(k)){reset(k,w,"Удержание прервано",false);return;}
        long now=System.currentTimeMillis();var attackers=new TreeSet<UUID>();boolean contested=false;
        for(var s:players.values())if(s.fresh(now)&&s.at(k)){
            if(s.town().equals(w.defender()))contested=true;
            if(s.soldier()&&s.town().equals(w.attacker())){var at=new Location(world,s.x(),s.y(),s.z());if(w.defender().equals(town(at))&&access.mobilized(s.actor()))attackers.add(s.actor());}
        }
        try{
            var cells=cells(w.defender(),k);
            if(standing(world,w.defender(),cells)){reset(k,w,"На участке ещё стоят части городских зданий",true);return;}
            var breach=breaches.get(k);
            if(breach!=null&&!cells.isEmpty()) {capture(w,k,breach.actor(),"BUILDING");return;}
            // Empty plots and ruins which predate this war require real occupation.
            var previous=holds.get(k);var next=ChunkHold.step(previous,attackers,contested,now);
            if(next==null){reset(k,w,contested?"Защитник вошёл на участок":"Нападавшие покинули участок",true);return;}
            holds.put(k,next);
            if(previous==null)event(k,w,attackers.first(),"STARTED","Пустой участок: удержать "+settings.holdSeconds()+" сек.");
            if(ChunkHold.complete(next,settings.holdSeconds()))capture(w,k,attackers.first(),"HOLD");
        }catch(Exception|LinkageError unavailable){reset(k,w,"Не удалось проверить городские здания",true);}
    }
    /** Called only after the authorized physical removal succeeded, on this block's region. */
    public void destroyed(RaidController.Permit permit,RaidStore.Key block){
        if(!enabled()||permit.siegeWar()||!access.valid(permit))return;
        var w=wars().stream().filter(r->r.id().equals(permit.raid())).findFirst().orElse(null);if(w==null||!access.battle(w))return;
        var k=new WarCaptureStore.Key(w.id(),block.world(),block.x()>>4,block.z()>>4);
        try{if(cells(w.defender(),k).contains(block))breaches.put(k,new Breach(permit.actor(),System.currentTimeMillis()));}catch(Exception|LinkageError ignored){}
    }
    private void capture(RaidStore.Raid w,WarCaptureStore.Key k,UUID actor,String method){
        if(captures.containsKey(k)||!pending.add(k))return;
        var c=new WarCaptureStore.Capture(k,w.attacker(),w.defender(),actor,method,System.currentTimeMillis(),"CAPTURED");
        store.capture(c).whenComplete((changed,error)->{pending.remove(k);if(error!=null||closed)return;
            if(Boolean.TRUE.equals(changed)){captures.put(k,c);holds.remove(k);breaches.remove(k);
                raids.notifyTown(w.attacker(),"Участок ["+k.x()+", "+k.z()+"] захвачен до конца войны. Здесь можно ставить и поджигать TNT.");
                raids.notifyTown(w.defender(),"Противник захватил участок ["+k.x()+", "+k.z()+"] до конца войны. Здесь он может поджигать TNT.");}
        });
    }
    private void reset(WarCaptureStore.Key k,RaidStore.Raid w,String reason,boolean log){var old=holds.remove(k);if(log&&old!=null)event(k,w,old.holders().iterator().next(),"INTERRUPTED",reason);}
    private boolean throttle(String key,long interval){long now=System.currentTimeMillis();Long old=notices.putIfAbsent(key,now);return old==null||now-old>=interval&&notices.replace(key,old,now);}
    private void event(WarCaptureStore.Key k,RaidStore.Raid w,UUID actor,String state,String detail){
        if(!throttle(k+":"+state,5000))return;
        store.event(k,w.attacker(),w.defender(),actor,state,detail);
    }
    void denied(Location at,UUID actor){var w=war(town(at));if(w==null||!throttle("tnt:"+key(w,at),10000))return;var k=key(w,at);String text="Поджиг TNT запрещён: разрушьте городские здания в этом чанке либо удержите пустой участок";
        store.event(k,w.attacker(),w.defender(),actor,"TNT_DENIED",text);
    }
    private static String coordinates(WarCaptureStore.Key k){return "война="+k.raid()+"; мир="+k.world()+"; чанк="+k.x()+","+k.z();}
    /** A committed event is acknowledged only after the admin journal fsync; restart retries its stable ID. */
    private void journal(){if(!journalling.compareAndSet(false,true))return;
        store.pendingLog().whenComplete((rows,error)->{if(error!=null||rows.isEmpty()){journalling.set(false);return;}var done=new ArrayList<Long>();
            for(var row:rows){String operation="war-chunk:"+row.key().raid()+":"+row.id();boolean denied=row.action().equals("TNT_DENIED");
                if(AuditTrail.record(plugin,"chunk-log",operation,denied?"RAID_TNT_DENIED":"RAID_CHUNK",denied?"DENIED":row.action(),row.actor()==null?AuditRecord.Party.system():AuditTrail.player(row.actor()),AuditTrail.town(row.attacker()),AuditTrail.town(row.defender()),"",0,"",row.detail()+"; "+coordinates(row.key())+"; время="+row.at()))done.add(row.id());
            }
            store.journalled(done).whenComplete((v,ackError)->journalling.set(false));
        });
    }
    public void status(Player player){if(!enabled()){raids.reply(player,"Военные участки отключены или ещё не готовы. Администратор может проверить /sieges raid status.");return;}sample(player);var at=player.getLocation();var w=war(town(at));
        if(w==null){raids.reply(player,"На этом участке нет объявленной войны. Действуют обычные правила города.");return;}
        var k=key(w,at);String prefix="Участок ["+k.x()+", "+k.z()+"]: ";
        if(!access.battle(w)){raids.reply(player,prefix+"идёт подготовка или бой приостановлен. TNT поджигать нельзя.");return;}
        if(captured(w,at)){raids.reply(player,prefix+"захвачен нападающими до конца войны. Установка и поджиг TNT разрешены участникам нападения.");return;}
        try{if(standing(at.getWorld(),w.defender(),cells(w.defender(),k))){raids.reply(player,prefix+"сначала разрушьте части всех городских зданий в этом чанке. Поджигайте TNT за границей города.");return;}}catch(Exception|LinkageError unavailable){raids.reply(player,"Проверка зданий временно недоступна. Захват и поджиг TNT приостановлены.");return;}
        var progress=holds.get(k);long elapsed=progress==null?0:progress.millis()/1000;
        raids.reply(player,prefix+"пустой. Удержание: "+elapsed+" / "+settings.holdSeconds()+" сек. Нужен мобилизованный нападающий в выживании; защитник сбрасывает отсчёт. TNT пока поджигается только за городом.");
    }
    @Override public void close(){closed=true;players.clear();holds.clear();breaches.clear();geometry.clear();}
}
