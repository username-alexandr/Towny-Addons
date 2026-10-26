package ru.neverland.mintcontracts.service;
import com.palmergames.bukkit.towny.object.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcontracts.integration.TownyHook;
import ru.neverland.mintcontracts.model.*;
import java.util.*;

public final class FieldWorkService {
    private final JavaPlugin plugin;private final ContractService contracts;private final TownyHook towny;
    private final Set<UUID> auditing=new HashSet<>();private final ScoutTracker scouts=new ScoutTracker();private int cursor;
    public FieldWorkService(JavaPlugin plugin,ContractService contracts,TownyHook towny){this.plugin=plugin;this.contracts=contracts;this.towny=towny;}
    private boolean eligible(Player p){return contracts.eligibleMode(p)&&p.hasPermission("mintcontracts.contribute");}
    public void placed(Player p,Block block){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        if(!eligible(p))return;Town town=towny.town(p);if(town==null)return;boolean changed=false;
        for(ActiveContract c:contracts.active(town.getUUID()))if(is(c,ContractType.ROAD)&&contracts.companyContributor(p,c)&&same(c.area(),block)){
            String key=WorkArea.key(block.getX(),block.getZ());
            if(c.area().required().contains(key)&&block.getType().name().equals(c.snapshot().target())){c.proof(key,new WorkProof(p.getUniqueId(),System.currentTimeMillis()));changed=true;}
        }if(changed)save();
    } }
    public void broken(Block block){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {boolean changed=false;
        for(ActiveContract c:contracts.repository().allActive())if(is(c,ContractType.ROAD)&&same(c.area(),block)){
            String key=WorkArea.key(block.getX(),block.getZ());if(c.proofs().containsKey(key)){c.removeProof(key);c.replaceWorkProgress(Map.of());changed=true;}
        }if(changed)save();
    } }
    public void move(Player p,Location from,Location to){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        if(to==null||!eligible(p)||p.isFlying()||p.isGliding()||p.isInsideVehicle()){reset(p);return;}
        if(from.getX()==to.getX()&&from.getY()==to.getY()&&from.getZ()==to.getZ())return;
        scouts.move(p.getUniqueId(),to.getWorld().getUID(),to.getBlockX()>>4,to.getBlockZ()>>4,System.currentTimeMillis());
    } }
    public void reset(Player p){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {scouts.reset(p.getUniqueId());} }
    public void pulse()throws java.io.IOException{ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        long now=System.currentTimeMillis();boolean changed=false;
        for(UUID actor:scouts.players()){
            Player p=Bukkit.getPlayer(actor);if(p==null){scouts.reset(actor);continue;}
            ru.neverland.core.SuiteScheduler.player(plugin,p,who->{try{
                if(!eligible(p)||p.isFlying()||p.isGliding()||p.isInsideVehicle()){scouts.reset(actor);return;}
            Location l=p.getLocation();var visit=scouts.ready(actor,l.getWorld().getUID(),l.getBlockX()>>4,l.getBlockZ()>>4,now,1000L*Math.max(5,plugin.getConfig().getInt("municipal.scout.dwell-seconds",30)));
            if(visit==null)return;Town town=towny.town(p);if(town==null)return;
            String key=WorkArea.key(visit.x(),visit.z());
            for(ActiveContract c:contracts.active(town.getUUID()))if(is(c,ContractType.SCOUT)&&contracts.companyContributor(p,c)&&c.area().world().equals(visit.world())&&c.area().required().contains(key)&&!c.proofs().containsKey(key)){
                if(!ScoutTracker.elapsed(visit,c.createdAt(),now,1000L*Math.max(5,plugin.getConfig().getInt("municipal.scout.dwell-seconds",30))))continue;
                c.proof(key,new WorkProof(actor,now));c.add(actor,1);contracts.saveField();contracts.completeField(c);
            }
            }catch(Exception ex){plugin.getLogger().warning("Разведка ожидает повторной проверки: "+ex.getMessage());}});
        }
        List<ActiveContract> roads=contracts.repository().allActive().stream().filter(c->is(c,ContractType.ROAD)).toList();
        int budget=4096,checked=0;
        while(!roads.isEmpty()&&checked<roads.size()){
            ActiveContract c=roads.get(Math.floorMod(cursor,roads.size()));int size=(c.area().maxX()-c.area().minX()+1)*(c.area().maxZ()-c.area().minZ()+1);
            if(size>budget)break;budget-=size;cursor++;checked++;
            if(now>=c.expiresAt())continue;
            Town town=towny.town(c.townId());World world=Bukkit.getWorld(c.area().world());if(town==null||world==null)continue;
            if(auditing.add(c.id()))auditRoad(c,town,world,now);
        }
        if(changed)contracts.saveField();
    } }
    private void auditRoad(ActiveContract c,Town town,World world,long now){
        var proofs=Map.copyOf(c.proofs());var cells=new HashMap<String,RoadAudit.Cell>();
        var chunks=new HashMap<Long,List<int[]>>();var area=c.area();
        for(int x=area.minX();x<=area.maxX();x++)for(int z=area.minZ();z<=area.maxZ();z++){
            long chunk=((long)(x>>4)<<32)|((z>>4)&0xffffffffL);chunks.computeIfAbsent(chunk,k->new ArrayList<>()).add(new int[]{x,z});
        }
        int[] remaining={chunks.size()};Material target=Material.matchMaterial(c.snapshot().target());
        for(var entry:chunks.entrySet()){
            int cx=(int)(entry.getKey()>>32),cz=(int)(long)entry.getKey();
            ru.neverland.core.SuiteScheduler.region(plugin,new Location(world,cx*16.0,area.y(),cz*16.0),()->{
                try{
                    for(int[] point:entry.getValue()){
                        int x=point[0],z=point[1],y=area.y();RoadAudit.Cell state=RoadAudit.Cell.UNKNOWN;
                        if(world.isChunkLoaded(cx,cz)){
                            state=RoadAudit.Cell.INVALID;
                            if(y>world.getMinHeight()&&y+2<world.getMaxHeight()&&WorldCoord.parseWorldCoord(new Location(world,x,y,z)).hasTown(town)){
                                Block b=world.getBlockAt(x,y,z);
                                if(b.getType()==target&&world.getBlockAt(x,y-1,z).getType().isSolid()&&world.getBlockAt(x,y+1,z).isPassable()&&world.getBlockAt(x,y+2,z).isPassable())state=RoadAudit.Cell.VALID;
                            }
                        }
                        cells.put(WorkArea.key(x,z),state);
                    }
                }finally{if(--remaining[0]==0){
                    auditing.remove(c.id());
                    if(!is(c,ContractType.ROAD)||System.currentTimeMillis()>=c.expiresAt()||!proofs.equals(c.proofs())||contracts.repository().allActive().stream().noneMatch(v->v==c))return;
                    RoadAudit.Result result=RoadAudit.inspect(area,proofs,now,1000L*Math.max(5,plugin.getConfig().getInt("municipal.road.stable-seconds",60)),(x,y,z)->cells.getOrDefault(WorkArea.key(x,z),RoadAudit.Cell.UNKNOWN));
                    try{
                        result.invalidProofs().forEach(c::removeProof);c.replaceWorkProgress(result.contributions());contracts.saveField();
                        if(result.complete())contracts.completeField(c);
                    }catch(Exception ex){plugin.getLogger().warning("Проверка дороги ожидает повторения: "+ex.getMessage());}
                }}
            });
        }
    }
    private boolean is(ActiveContract c,ContractType type){return !c.timer().paused()&&c.funded()&&c.settlementStatus()==null&&c.snapshot()!=null&&c.snapshot().type()==type&&c.area()!=null;}
    private boolean same(WorkArea a,Block b){return a.world().equals(b.getWorld().getUID())&&a.y()==b.getY()&&a.contains(b.getX(),b.getZ());}
    private void save(){try{contracts.saveField();}catch(Exception ex){plugin.getLogger().severe("Сохранение полевых заданий остановлено: "+ex.getMessage());}}
}
