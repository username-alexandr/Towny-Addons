package ru.neverland.townydistricts.service;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townydistricts.model.*;
import java.util.*;
public final class BorderPreview {
    private final DistrictService service;private final Map<UUID,Long> players=new HashMap<>();private final BukkitTask task;
    public BorderPreview(Plugin plugin,DistrictService service){this.service=service;task=Bukkit.getScheduler().runTaskTimer(plugin,this::render,20,20);}
    public void show(Player player){players.put(player.getUniqueId(),System.currentTimeMillis()+30000);render();}
    public void stop(){players.clear();task.cancel();}
    private void render(){long now=System.currentTimeMillis();for(var entry:new ArrayList<>(players.entrySet())){
        Player p=Bukkit.getPlayer(entry.getKey());if(p==null||entry.getValue()<now||!p.hasPermission("neverlandtownydistricts.use")){players.remove(entry.getKey());continue;}
        var town=service.town(p);if(town==null)continue;int count=0,size=service.cellSize();var location=p.getLocation();
        for(District d:service.districts(town.getUUID())){
            var dust=new Particle.DustOptions(color(d.type()),1.2f);
            for(Cell c:d.cells()){
                if(!c.world().equals(p.getWorld().getUID()))continue;double x=(double)c.x()*size,z=(double)c.z()*size;
                if(Math.abs(x-location.getX())>64+size||Math.abs(z-location.getZ())>64+size)continue;
                for(int side=0;side<4;side++){
                    Cell n=switch(side){case 0->new Cell(c.world(),c.x()-1,c.z());case 1->new Cell(c.world(),c.x()+1,c.z());case 2->new Cell(c.world(),c.x(),c.z()-1);default->new Cell(c.world(),c.x(),c.z()+1);};
                    if(d.cells().contains(n))continue;
                    for(int offset=0;offset<=size;offset+=Math.max(1,size/8)){
                        double px=side<2?x+(side==1?size:0):x+offset,pz=side<2?z+offset:z+(side==3?size:0);
                        if(Math.hypot(px-location.getX(),pz-location.getZ())>64||count>=400)continue;
                        p.spawnParticle(Particle.DUST,px,location.getY()+0.15,pz,1,0,0,0,0,dust);count++;
                    }
                }
            }
        }
    }}
    private Color color(DistrictType type){return switch(type){case RESIDENTIAL->Color.LIME;case INDUSTRIAL->Color.ORANGE;case COMMERCIAL->Color.YELLOW;case MILITARY->Color.RED;case PORT->Color.AQUA;case AGRICULTURAL->Color.GREEN;case ADMINISTRATIVE->Color.FUCHSIA;};}
}
