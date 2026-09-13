package ru.neverland.mintevents.service;

import com.palmergames.bukkit.towny.object.Town;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintevents.integration.TownyHook;
import ru.neverland.mintevents.model.ActiveEvent;
import ru.neverland.mintevents.model.FireDamage;

/** Controlled block fires. Vanilla fire is never placed, so unrelated blocks cannot spread/burn. */
public final class FireService {
    private record Hotspot(UUID town,UUID world,int x,int y,int z,long expires,boolean destroyed) {
        UUID id(){return FireDamage.id(world,x,y,z);}
    }
    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final FireDamageRepository damage;
    private final Map<UUID,Hotspot> hotspots=new HashMap<>();
    private final Map<UUID,Long> cooled=new HashMap<>();
    private long warningAt;

    public FireService(JavaPlugin plugin,TownyHook towny){
        this.plugin=plugin;this.towny=towny;damage=new FireDamageRepository(plugin.getDataFolder().toPath().resolve("fire-damage.yml"));reload();
    }
    public void reload(){try{damage.load();}catch(IOException e){throw new IllegalStateException("Журнал последствий пожара не загружен",e);}hotspots.clear();cooled.clear();}
    public FireDamageRepository repository(){return damage;}
    public List<FireDamage> damage(UUID town){return damage.town(town);}
    public boolean requiresRepair(UUID world,int x,int y,int z){return damage.pending(world,x,y,z);}
    public int burning(UUID town){return (int)hotspots.values().stream().filter(h->h.town().equals(town)).count();}
    public void finish(UUID town){hotspots.values().removeIf(h->h.town().equals(town));}
    public void clearVisuals(){hotspots.clear();cooled.clear();}

    public void tick(Town town,ActiveEvent event,boolean ignite,List<Player> residents){
        long now=System.currentTimeMillis();hotspots.values().removeIf(h->h.expires()<now);cooled.values().removeIf(until->until<now);
        if(!plugin.getConfig().getBoolean("gameplay.fire.enabled",true))return;
        if(ignite&&damage.writable()){
            int limit=Math.max(1,Math.min(12,plugin.getConfig().getInt("gameplay.fire.max-hotspots",6)));
            if(burning(town.getUUID())<limit){
                Block target=findTarget(town,residents);
                if(target!=null){
                    double chance=Math.max(0,Math.min(1,plugin.getConfig().getDouble("gameplay.fire.damage-chance",.25)))*(1-event.protection());
                    int maximum=Math.max(0,Math.min(256,plugin.getConfig().getInt("gameplay.fire.max-damaged-blocks",24)));
                    boolean destroy=damage(town.getUUID()).size()<maximum&&ThreadLocalRandom.current().nextDouble()<chance;
                    try{ignite(town,event,target,destroy);}catch(IOException|RuntimeException error){warn(error);}
                }
            }
        }
        for(Hotspot h:new ArrayList<>(hotspots.values()))if(h.town().equals(town.getUUID())){
            World world=loaded(h.world(),h.x(),h.y(),h.z());if(world==null)continue;
            Block block=world.getBlockAt(h.x(),h.y(),h.z());Town owner=towny.townAt(block.getLocation());
            if(owner==null||!owner.getUUID().equals(h.town())||wet(block)) {hotspots.remove(h.id());continue;}
            if(!h.destroyed()&&!FireMaterials.combustible(block.getType().name())){hotspots.remove(h.id());continue;}
            world.spawnParticle(Particle.FLAME,h.x()+.5,h.y()+.65,h.z()+.5,8,.55,.45,.55,.01);
            world.spawnParticle(Particle.LARGE_SMOKE,h.x()+.5,h.y()+1.1,h.z()+.5,3,.35,.3,.35,.005);
            if(ignite)world.playSound(block.getLocation(),Sound.BLOCK_FIRE_AMBIENT,.45f,1.0f);
        }
    }

    /** Used by the scheduler and the disposable runtime probe; ownership is always checked here. */
    public boolean ignite(Town town,ActiveEvent event,Block block,boolean destroy) throws IOException {
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Пожар требует основного потока");
        if(!damage.writable())throw new IOException("Журнал пожара недоступен");
        if(town==null||event==null||!town.getUUID().equals(event.townId())||!eligible(town,block))return false;
        UUID id=FireDamage.id(block.getWorld().getUID(),block.getX(),block.getY(),block.getZ());
        if(hotspots.containsKey(id)||cooled.getOrDefault(id,0L)>System.currentTimeMillis()||damage.get(id)!=null)return false;
        if(destroy){
            int maximum=Math.max(0,Math.min(256,plugin.getConfig().getInt("gameplay.fire.max-damaged-blocks",24)));
            if(damage(town.getUUID()).size()>=maximum)destroy=false;
        }
        if(destroy){
            FireDamage record=new FireDamage(town.getUUID(),block.getWorld().getUID(),block.getX(),block.getY(),block.getZ(),block.getBlockData().getAsString(),event.startedAt());
            if(!damage.prepare(record))return false; // force + atomic replacement before touching the world
            block.setType(Material.AIR,false); // no drops and no uncontrolled vanilla fire/physics
        }
        long seconds=Math.max(5,Math.min(120,plugin.getConfig().getLong("gameplay.fire.hotspot-seconds",25)));
        hotspots.put(id,new Hotspot(town.getUUID(),block.getWorld().getUID(),block.getX(),block.getY(),block.getZ(),System.currentTimeMillis()+seconds*1000,destroy));return true;
    }
    private boolean eligible(Town town,Block block){
        if(block==null||!block.getWorld().isChunkLoaded(block.getX()>>4,block.getZ()>>4)||!FireMaterials.combustible(block.getType().name())||block.getState() instanceof TileState||wet(block))return false;
        Town owner=towny.townAt(block.getLocation());if(owner==null||!owner.getUUID().equals(town.getUUID()))return false;
        // A lone natural trunk is not a wooden building. Trees/leaves are never selected as fire anchors.
        if(block.getType().name().endsWith("_LOG")||block.getType().name().endsWith("_WOOD")){
            boolean built=false;
            for(int x=-2;x<=2&&!built;x++)for(int y=-2;y<=2&&!built;y++)for(int z=-2;z<=2;z++){
                int yy=block.getY()+y,xx=block.getX()+x,zz=block.getZ()+z;
                if(yy<block.getWorld().getMinHeight()||yy>=block.getWorld().getMaxHeight()||!block.getWorld().isChunkLoaded(xx>>4,zz>>4))continue;
                if(FireMaterials.structuralMarker(block.getWorld().getBlockAt(xx,yy,zz).getType().name())){built=true;break;}
            }
            return built;
        }
        return true;
    }
    private boolean wet(Block block){
        if(block.getType()==Material.WATER||block.getBlockData() instanceof Waterlogged data&&data.isWaterlogged())return true;
        for(int[] d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}}){
            int x=block.getX()+d[0],y=block.getY()+d[1],z=block.getZ()+d[2];World w=block.getWorld();
            if(y>=w.getMinHeight()&&y<w.getMaxHeight()&&w.isChunkLoaded(x>>4,z>>4)&&w.getBlockAt(x,y,z).getType()==Material.WATER)return true;
        }
        return false;
    }
    private Block findTarget(Town town,List<Player> residents){
        List<Location> anchors=new ArrayList<>();
        for(Player p:residents){Town at=towny.townAt(p.getLocation());if(at!=null&&at.getUUID().equals(town.getUUID()))anchors.add(p.getLocation());}
        if(anchors.isEmpty())return null;
        int radius=Math.max(4,Math.min(32,plugin.getConfig().getInt("gameplay.fire.search-radius",16)));
        int attempts=Math.max(16,Math.min(512,plugin.getConfig().getInt("gameplay.fire.search-attempts",128)));
        var random=ThreadLocalRandom.current();
        for(int i=0;i<attempts;i++){
            Location a=anchors.get(random.nextInt(anchors.size()));World w=a.getWorld();int x=a.getBlockX()+random.nextInt(-radius,radius+1),z=a.getBlockZ()+random.nextInt(-radius,radius+1),y=a.getBlockY()+random.nextInt(-5,11);
            if(y<w.getMinHeight()||y>=w.getMaxHeight()||!w.isChunkLoaded(x>>4,z>>4))continue;
            Block b=w.getBlockAt(x,y,z);if(eligible(town,b)&&!hotspots.containsKey(FireDamage.id(w.getUID(),x,y,z)))return b;
        }
        return null;
    }
    public int extinguish(Player player,Location water){
        Town town=towny.town(player);if(town==null||water.getWorld()==null)return 0;
        int count=0;for(Hotspot h:new ArrayList<>(hotspots.values())){
            if(!h.town().equals(town.getUUID())||!h.world().equals(water.getWorld().getUID()))continue;
            if(new Location(water.getWorld(),h.x()+.5,h.y()+.5,h.z()+.5).distanceSquared(water)>9)continue;
            hotspots.remove(h.id());cooled.put(h.id(),System.currentTimeMillis()+60_000);count++;
        }
        return count;
    }
    public boolean near(Player player){return hotspots.values().stream().anyMatch(h->h.world().equals(player.getWorld().getUID())&&new Location(player.getWorld(),h.x()+.5,h.y()+.5,h.z()+.5).distanceSquared(player.getLocation())<=25);}
    public boolean placed(FireDamage d){World world=loaded(d.world(),d.x(),d.y(),d.z());return world!=null&&world.getBlockAt(d.x(),d.y(),d.z()).getType().name().equals(d.material());}
    public int confirmRepairs(Player player,boolean fireActive) throws IOException {
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Ремонт требует основного потока");
        if(!player.hasPermission("mintevents.repair"))throw new IllegalArgumentException("Нет права подтверждать ремонт");
        Town town=towny.town(player);if(town==null)throw new IllegalArgumentException("Сначала вступите в город");
        if(fireActive)throw new IllegalArgumentException("Сначала потушите городской пожар");
        if(!damage.writable())throw new IOException("Журнал ремонта заблокирован");
        Set<UUID> repaired=new HashSet<>();Set<World> worlds=new HashSet<>();
        for(FireDamage d:damage(town.getUUID())){
            World world=loaded(d.world(),d.x(),d.y(),d.z());if(world==null)continue;
            Block b=world.getBlockAt(d.x(),d.y(),d.z());Town owner=towny.townAt(b.getLocation());
            if(owner==null||!owner.getUUID().equals(town.getUUID())||!placed(d))continue;
            b.setBlockData(Bukkit.createBlockData(d.blockData()),false);worlds.add(world);repaired.add(d.id());
        }
        if(!repaired.isEmpty()){
            player.saveData();for(World world:worlds)world.save(); // once per repair batch, never once per block
            damage.confirm(repaired);
        }
        return repaired.size();
    }
    public void highlight(Player player,UUID id){
        FireDamage d=damage.get(id);Town town=towny.town(player);
        if(d==null||town==null||!d.town().equals(town.getUUID()))return;
        World w=loaded(d.world(),d.x(),d.y(),d.z());
        player.sendMessage("§eМесто ремонта: §f"+d.x()+" / "+d.y()+" / "+d.z());
        if(w==null||!w.equals(player.getWorld())||player.getLocation().distanceSquared(new Location(w,d.x(),d.y(),d.z()))>64*64){player.sendMessage("§7Подойдите ближе к указанным координатам.");return;}
        var dust=new Particle.DustOptions(Color.fromRGB(255,196,92),1.4f);
        for(int repeat=0;repeat<10;repeat++)Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(!player.isOnline()||!player.getWorld().equals(w)||damage.get(id)==null||!w.isChunkLoaded(d.x()>>4,d.z()>>4))return;
            for(double t=0;t<=1.001;t+=.25)for(int a=0;a<=1;a++)for(int b=0;b<=1;b++){
                player.spawnParticle(Particle.DUST,d.x()+t,d.y()+a,d.z()+b,1,0,0,0,0,dust);
                player.spawnParticle(Particle.DUST,d.x()+a,d.y()+t,d.z()+b,1,0,0,0,0,dust);
                player.spawnParticle(Particle.DUST,d.x()+a,d.y()+b,d.z()+t,1,0,0,0,0,dust);
            }
        },repeat*10L);
    }
    private World loaded(UUID id,int x,int y,int z){World w=Bukkit.getWorld(id);return w!=null&&y>=w.getMinHeight()&&y<w.getMaxHeight()&&w.isChunkLoaded(x>>4,z>>4)?w:null;}
    private void warn(Exception error){long now=System.currentTimeMillis();if(now-warningAt>60_000){warningAt=now;plugin.getLogger().log(java.util.logging.Level.WARNING,"Пожар остановлен до восстановления журнала повреждений",error);}}
}
