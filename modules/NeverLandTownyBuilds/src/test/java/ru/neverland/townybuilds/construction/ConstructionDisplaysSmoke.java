package ru.neverland.townybuilds.construction;

import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.plugin.*;
import io.papermc.paper.threadedregions.scheduler.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

/** Strict region ownership: stale display getters throw, teardown must use its entity scheduler. */
public final class ConstructionDisplaysSmoke {
    interface Call {Object apply(String name,Object[] args);}
    static <T>T stub(Class<T> type,Call call){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(self,m,a)->switch(m.getName()){
        case "equals"->self==a[0];case "hashCode"->System.identityHashCode(self);case "toString"->type.getSimpleName();default->call.apply(m.getName(),a);
    }));}
    static Object bad(String name){throw new AssertionError("Unexpected API access: "+name);}
    static int checks,spawned,removed,shown;static boolean chunksOwned=true;
    static final Set<Entity> foreign=Collections.newSetFromMap(new IdentityHashMap<>());
    static final List<Runnable> scheduled=new ArrayList<>();
    static final List<Map<String,Object>> flags=new ArrayList<>();
    static void ok(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    static BlockDisplay display(BlockData initial) {
        Map<String,Object> values=new HashMap<>();values.put("valid",true);values.put("block",initial);flags.add(values);
        BlockDisplay[] self=new BlockDisplay[1];UUID id=UUID.randomUUID();
        var scheduler=stub(EntityScheduler.class,(name,args)->{
            if(!name.equals("runDelayed"))return bad(name);
            scheduled.add(()->((Consumer<ScheduledTask>)args[1]).accept(null));
            return stub(ScheduledTask.class,(n,a)->switch(n){case "isCancelled"->false;default->bad(n);});
        });
        self[0]=stub(BlockDisplay.class,(name,args)->{
            if(name.equals("getUniqueId"))return id;
            if(name.equals("getScheduler"))return scheduler;
            if(foreign.contains(self[0]))throw new AssertionError("Foreign display accessed: "+name);
            return switch(name) {
                case "isValid"->values.get("valid");case "getBlock"->values.get("block");
                case "setBlock"->{values.put("block",args[0]);yield null;}
                case "remove"->{values.put("valid",false);removed++;yield null;}
                case "setPersistent","setVisibleByDefault","setInvulnerable","setGravity","setTransformationMatrix","setBrightness","setShadowRadius","setViewRange"->{values.put(name,args[0]);yield null;}
                default->bad(name);
            };
        });return self[0];
    }
    public static void main(String[] args) throws Exception {
        Plugin plugin=stub(Plugin.class,(n,a)->switch(n){case "isEnabled"->true;case "getName"->"NeverLandTownyBuilds";default->bad(n);});
        var manager=stub(PluginManager.class,(n,a)->n.equals("getPlugin")?plugin:bad(n));
        var server=stub(Server.class,(n,a)->switch(n){
            case "getLogger"->java.util.logging.Logger.getLogger("display-test");case "getName","getVersion","getBukkitVersion"->"test";
            case "getPluginManager"->manager;
            case "isStopping"->false;
            case "isOwnedByCurrentRegion"->a[0] instanceof Entity e?!foreign.contains(e):chunksOwned;
            default->bad(n);
        });var serverField=Bukkit.class.getDeclaredField("server");serverField.setAccessible(true);serverField.set(null,server);
        UUID worldId=UUID.randomUUID();List<BlockDisplay> entities=new ArrayList<>();
        World world=stub(World.class,(n,a)->switch(n){
            case "getUID"->worldId;case "isChunkLoaded"->true;
            case "spawn"->{spawned++;var d=display(null);entities.add(d);((Consumer<BlockDisplay>)a[a.length-1]).accept(d);yield d;}
            default->bad(n);
        });
        UUID playerId=UUID.randomUUID();Player player=stub(Player.class,(n,a)->switch(n){case "getUniqueId"->playerId;case "getWorld"->world;case "showEntity"->{shown++;yield null;}default->bad(n);});
        BlockData block=stub(BlockData.class,(n,a)->n.equals("getMaterial")?Material.STONE:bad(n));
        var first=new ConstructionBlockDisplays.Hint(new Location(world,1,70,1),block);
        var second=new ConstructionBlockDisplays.Hint(new Location(world,2,70,1),block);
        var renderer=new ConstructionBlockDisplays(plugin);
        renderer.update(player,List.of(first,second),2,2,0.85f);
        ok(spawned==2&&shown==2&&renderer.size()==2,"personal display layer");
        ok(Boolean.FALSE.equals(flags.get(0).get("setPersistent"))&&Boolean.FALSE.equals(flags.get(0).get("setVisibleByDefault")),"temporary and private entities");
        renderer.update(player,List.of(first,second),2,2,0.85f);ok(spawned==2&&removed==0,"unchanged hints are reused");
        renderer.update(player,List.of(second),2,2,0.85f);ok(removed==1&&renderer.size()==1,"placed block hint disappears");
        renderer.update(player,List.of(first,second),1,1,0.85f);ok(renderer.size()==1,"display limit");
        foreign.addAll(entities);renderer.remove(playerId);
        ok(renderer.size()==0&&scheduled.size()==1,"foreign entity cleanup queued without touching its state");
        foreign.clear();for(Runnable task:List.copyOf(scheduled))task.run();scheduled.clear();
        ok(removed==3,"scheduled cleanup removed old entities");
        chunksOwned=false;renderer.update(player,List.of(first),2,2,0.85f);ok(renderer.size()==0,"foreign chunks are not spawned into");
        chunksOwned=true;renderer.update(player,List.of(first),2,2,0.85f);renderer.clear();
        ok(renderer.size()==0&&removed==4,"module stop/reload cleans all hints");
        renderer.update(player,List.of(first),2,2,0.85f);
        flags.get(flags.size()-1).put("valid",false);renderer.update(player,List.of(first),2,2,0.85f);
        ok(spawned==6&&renderer.size()==1,"chunk unload or external removal recreates hint");
        renderer.clear();
        System.out.println("ConstructionDisplaysSmoke: "+checks+" checks passed");
    }
}
