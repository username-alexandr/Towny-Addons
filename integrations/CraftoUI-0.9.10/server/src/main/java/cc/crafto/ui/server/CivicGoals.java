package cc.crafto.ui.server;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.block.Block;
import static cc.crafto.ui.Protocol.*;
/** Unique block coordinates prevent place/break farming. Existing blocks are never attributed. */
final class CivicGoals implements Listener {
    record Goal(String kind,String id,long pinned){}
    private final Path file;private final Map<String,Goal> goals=new HashMap<>();
    private final Map<String,String> owners=new HashMap<>(),roles=new HashMap<>();
    private final Map<String,Map<String,org.bukkit.Material>> plans=new ConcurrentHashMap<>();
    private boolean loaded,dirty;
    CivicGoals(Path file){this.file=file;}
    synchronized void load()throws Exception{
        if(Files.exists(file)){
            if(Files.size(file)>16*1024*1024)throw new IllegalArgumentException("Goals file too large");
            var j=JsonParser.parseString(Files.readString(file)).getAsJsonObject();if(integer(j,"schema",0)!=1)throw new IllegalArgumentException("Unknown goals schema");
            if(j.has("goals"))for(var e:j.getAsJsonObject("goals").entrySet())goals.put(e.getKey(),JSON.fromJson(e.getValue(),Goal.class));
            if(j.has("owners"))for(var e:j.getAsJsonObject("owners").entrySet())owners.put(e.getKey(),e.getValue().getAsString());
            if(j.has("roles"))for(var e:j.getAsJsonObject("roles").entrySet())roles.put(e.getKey(),e.getValue().getAsString());
        }loaded=true;
    }
    synchronized Goal get(UUID town){return town==null?null:goals.get(town.toString());}
    synchronized Set<UUID> towns(){var out=new HashSet<UUID>();goals.keySet().forEach(v->out.add(UUID.fromString(v)));return out;}
    synchronized boolean ready(){return loaded;}
    synchronized void pinIfUnchanged(UUID town,String expected,Goal goal)throws Exception{
        if(!String.valueOf(get(town)).equals(expected))throw new IllegalArgumentException("Общая цель уже изменена другим игроком. Откройте её заново.");
        pin(town,goal);
    }
    synchronized void pin(UUID town,Goal goal)throws Exception{
        if(!loaded)throw new IllegalArgumentException("Хранилище целей недоступно");
        String key=town.toString();Goal old=goals.get(key);if(goal==null)goals.remove(key);else goals.put(key,goal);
        dirty=true;try{flush();}catch(Exception e){if(old==null)goals.remove(key);else goals.put(key,old);throw e;}
        plans.remove(key);
    }
    void plan(UUID town,CityBuildReader.Result builds){
        Goal goal=get(town);if(goal==null||!goal.kind().equals("build")){plans.remove(town.toString());return;}
        var site=builds.sites().stream().filter(s->s.id().equals(goal.id())).findFirst();if(site.isPresent())plans.put(town.toString(),site.get().expected());else plans.remove(town.toString());
    }
    synchronized int contribution(UUID town,UUID player,CityBuildReader.SiteInfo site){
        Goal goal=get(town);if(goal==null)return 0;String prefix=town+"/"+goal.pinned()+"/";
        return (int)site.installed().stream().filter(pos->player.toString().equals(owners.get(prefix+pos))).count();
    }
    synchronized List<String> roleUpdates(UUID player,String current){
        if(!loaded)return List.of();String old=roles.put(player.toString(),current);if(!Objects.equals(old,current))dirty=true;
        return old!=null&&!old.equals(current)?List.of("Ваши должности изменились: "+current):List.of();
    }
    private static String pos(Block b){return b.getWorld().getUID()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public synchronized void placed(BlockPlaceEvent e){
        if(!loaded)return;String pos=pos(e.getBlockPlaced());
        for(var entry:plans.entrySet())if(e.getBlockPlaced().getType()==entry.getValue().get(pos)){
            Goal goal=goals.get(entry.getKey());if(goal==null)continue;
            owners.put(entry.getKey()+"/"+goal.pinned()+"/"+pos,e.getPlayer().getUniqueId().toString());dirty=true;
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public synchronized void broken(BlockBreakEvent e){remove(pos(e.getBlock()));}
    private void remove(String pos){if(owners.keySet().removeIf(k->k.endsWith("/"+pos)))dirty=true;}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public synchronized void exploded(BlockExplodeEvent e){for(Block b:e.blockList())remove(pos(b));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public synchronized void exploded(org.bukkit.event.entity.EntityExplodeEvent e){for(Block b:e.blockList())remove(pos(b));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public synchronized void piston(BlockPistonExtendEvent e){for(Block b:e.getBlocks())remove(pos(b));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public synchronized void piston(BlockPistonRetractEvent e){for(Block b:e.getBlocks())remove(pos(b));}
    synchronized void flush()throws Exception{
        if(!loaded||!dirty)return;Files.createDirectories(file.getParent());Path temp=file.resolveSibling(file.getFileName()+".tmp");
        Files.writeString(temp,JSON.toJson(object("schema",1,"goals",goals,"owners",owners,"roles",roles)),StandardCharsets.UTF_8);
        try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}dirty=false;
    }
}
