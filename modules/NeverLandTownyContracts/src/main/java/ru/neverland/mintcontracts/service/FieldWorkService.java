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
    private final ScoutTracker scouts=new ScoutTracker();private int cursor;
    public FieldWorkService(JavaPlugin plugin,ContractService contracts,TownyHook towny){this.plugin=plugin;this.contracts=contracts;this.towny=towny;}
    private boolean eligible(Player p){return contracts.eligibleMode(p)&&p.hasPermission("mintcontracts.contribute");}
    public void placed(Player p,Block block){
        if(!eligible(p))return;Town town=towny.town(p);if(town==null)return;boolean changed=false;
        for(ActiveContract c:contracts.active(town.getUUID()))if(is(c,ContractType.ROAD)&&contracts.companyContributor(p,c)&&same(c.area(),block)){
            String key=WorkArea.key(block.getX(),block.getZ());
            if(c.area().required().contains(key)&&block.getType().name().equals(c.snapshot().target())){c.proof(key,new WorkProof(p.getUniqueId(),System.currentTimeMillis()));changed=true;}
        }if(changed)save();
    }
    public void broken(Block block){boolean changed=false;
        for(ActiveContract c:contracts.repository().allActive())if(is(c,ContractType.ROAD)&&same(c.area(),block)){
            String key=WorkArea.key(block.getX(),block.getZ());if(c.proofs().containsKey(key)){c.removeProof(key);c.replaceWorkProgress(Map.of());changed=true;}
        }if(changed)save();
    }
    public void move(Player p,Location from,Location to){
        if(to==null||!eligible(p)||p.isFlying()||p.isGliding()||p.isInsideVehicle()){reset(p);return;}
        if(from.getX()==to.getX()&&from.getY()==to.getY()&&from.getZ()==to.getZ())return;
        scouts.move(p.getUniqueId(),to.getWorld().getUID(),to.getBlockX()>>4,to.getBlockZ()>>4,System.currentTimeMillis());
    }
    public void reset(Player p){scouts.reset(p.getUniqueId());}
    public void pulse()throws java.io.IOException{
        long now=System.currentTimeMillis();boolean changed=false;
        for(UUID actor:scouts.players()){
            Player p=Bukkit.getPlayer(actor);if(p==null||!eligible(p)||p.isFlying()||p.isGliding()||p.isInsideVehicle()){scouts.reset(actor);continue;}
            Location l=p.getLocation();var visit=scouts.ready(actor,l.getWorld().getUID(),l.getBlockX()>>4,l.getBlockZ()>>4,now,1000L*Math.max(5,plugin.getConfig().getInt("municipal.scout.dwell-seconds",30)));
            if(visit==null)continue;Town town=towny.town(p);if(town==null)continue;
            String key=WorkArea.key(visit.x(),visit.z());
            for(ActiveContract c:contracts.active(town.getUUID()))if(is(c,ContractType.SCOUT)&&contracts.companyContributor(p,c)&&c.area().world().equals(visit.world())&&c.area().required().contains(key)&&!c.proofs().containsKey(key)){
                if(!ScoutTracker.elapsed(visit,c.createdAt(),now,1000L*Math.max(5,plugin.getConfig().getInt("municipal.scout.dwell-seconds",30))))continue;
                c.proof(key,new WorkProof(actor,now));c.add(actor,1);contracts.saveField();contracts.completeField(c);
            }
        }
        List<ActiveContract> roads=contracts.repository().allActive().stream().filter(c->is(c,ContractType.ROAD)).toList();
        int budget=4096,checked=0;
        while(!roads.isEmpty()&&checked<roads.size()){
            ActiveContract c=roads.get(Math.floorMod(cursor,roads.size()));int size=(c.area().maxX()-c.area().minX()+1)*(c.area().maxZ()-c.area().minZ()+1);
            if(size>budget)break;budget-=size;cursor++;checked++;
            if(now>=c.expiresAt())continue;
            Town town=towny.town(c.townId());World world=Bukkit.getWorld(c.area().world());if(town==null||world==null)continue;
            Material target=Material.matchMaterial(c.snapshot().target());
            RoadAudit.Result result=RoadAudit.inspect(c.area(),c.proofs(),now,1000L*Math.max(5,plugin.getConfig().getInt("municipal.road.stable-seconds",60)),(x,y,z)->{
                if(!world.isChunkLoaded(x>>4,z>>4))return RoadAudit.Cell.UNKNOWN;
                if(y<=world.getMinHeight()||y+2>=world.getMaxHeight()||!WorldCoord.parseWorldCoord(new Location(world,x,y,z)).hasTown(town))return RoadAudit.Cell.INVALID;
                Block b=world.getBlockAt(x,y,z);return b.getType()==target&&world.getBlockAt(x,y-1,z).getType().isSolid()&&world.getBlockAt(x,y+1,z).isPassable()&&world.getBlockAt(x,y+2,z).isPassable()?RoadAudit.Cell.VALID:RoadAudit.Cell.INVALID;
            });
            if(!result.invalidProofs().isEmpty()||!result.contributions().equals(c.contributions())){result.invalidProofs().forEach(c::removeProof);c.replaceWorkProgress(result.contributions());changed=true;}
            if(result.complete()){contracts.saveField();contracts.completeField(c);changed=false;}
        }
        if(changed)contracts.saveField();
    }
    private boolean is(ActiveContract c,ContractType type){return c.funded()&&c.settlementStatus()==null&&c.snapshot()!=null&&c.snapshot().type()==type&&c.area()!=null;}
    private boolean same(WorkArea a,Block b){return a.world().equals(b.getWorld().getUID())&&a.y()==b.getY()&&a.contains(b.getX(),b.getZ());}
    private void save(){try{contracts.saveField();}catch(Exception ex){plugin.getLogger().severe("Сохранение полевых заданий остановлено: "+ex.getMessage());}}
}
