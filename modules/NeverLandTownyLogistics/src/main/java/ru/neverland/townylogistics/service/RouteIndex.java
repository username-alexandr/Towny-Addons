package ru.neverland.townylogistics.service;
import java.util.*;
import ru.neverland.townylogistics.model.*;
import ru.neverland.townylogistics.model.Network.*;
/** A block/chunk event addresses only intersecting network links. Long legacy links use endpoints to bound indexing. */
public final class RouteIndex {
    public record Chunk(UUID world,int x,int z){}
    private final Map<Chunk,Map<UUID,Set<Link>>> chunks=new HashMap<>();private final Map<UUID,Set<Chunk>> towns=new HashMap<>();
    public void update(Network network){remove(network.town());var touched=new HashSet<Chunk>();for(var link:network.links()){
        var a=network.nodes().get(link.a());var b=network.nodes().get(link.b());if(a==null||b==null||!a.point().world().equals(b.point().world()))continue;int ax=(int)Math.floor(a.point().x()/16),az=(int)Math.floor(a.point().z()/16),bx=(int)Math.floor(b.point().x()/16),bz=(int)Math.floor(b.point().z()/16);
        if(Math.abs(ax-bx)>4||Math.abs(az-bz)>4){put(network.town(),link,new Chunk(a.point().world(),ax,az),touched);put(network.town(),link,new Chunk(b.point().world(),bx,bz),touched);}else for(int x=Math.min(ax,bx);x<=Math.max(ax,bx);x++)for(int z=Math.min(az,bz);z<=Math.max(az,bz);z++)put(network.town(),link,new Chunk(a.point().world(),x,z),touched);
    }towns.put(network.town(),Set.copyOf(touched));}
    private void put(UUID town,Link link,Chunk chunk,Set<Chunk> touched){chunks.computeIfAbsent(chunk,k->new HashMap<>()).computeIfAbsent(town,k->new HashSet<>()).add(link);touched.add(chunk);}
    public void remove(UUID town){for(var key:towns.getOrDefault(town,Set.of())){var entry=chunks.get(key);if(entry!=null){entry.remove(town);if(entry.isEmpty())chunks.remove(key);}}towns.remove(town);}
    public Map<UUID,Set<Link>> affected(UUID world,int x,int z){var result=new HashMap<UUID,Set<Link>>();chunks.getOrDefault(new Chunk(world,x,z),Map.of()).forEach((town,links)->result.put(town,Set.copyOf(links)));return Map.copyOf(result);}
}
