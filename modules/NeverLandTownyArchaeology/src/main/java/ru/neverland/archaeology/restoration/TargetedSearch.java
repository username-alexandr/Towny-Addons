package ru.neverland.archaeology.restoration;
import java.util.*;
import ru.neverland.archaeology.model.*;
/** Metadata only: no foreign chunk loads or promises that a particular stored loot item is still there. */
public final class TargetedSearch {
    private TargetedSearch(){}
    public record Hint(UUID site,String siteType,String siteName,UUID world,int x,int y,int z,long knownAt,long distanceSquared,Set<String> possibleArtifacts){}
    public static List<Hint> find(String artifact,UUID world,int x,int y,int z,int radius,Collection<DigSite> sites,Collection<SiteDefinition> definitions){
        if(radius<1||radius>5000||world==null)return List.of();var byId=new HashMap<String,SiteDefinition>();for(var definition:definitions)if(definition.artifactWeights().getOrDefault(artifact,0)>0)byId.put(definition.id(),definition);
        var out=new ArrayList<Hint>();for(var site:sites){if(site.completed()||!site.worldId().equals(world))continue;var definition=byId.get(site.type());if(definition==null)continue;long dx=(long)site.x()-x,dy=(long)site.y()-y,dz=(long)site.z()-z;if(Math.abs(dx)>radius||Math.abs(dy)>radius||Math.abs(dz)>radius)continue;long distance=dx*dx+dy*dy+dz*dz;if(distance>(long)radius*radius)continue;out.add(new Hint(site.id(),site.type(),definition.name(),world,site.x(),site.y(),site.z(),site.createdAt(),distance,Set.copyOf(definition.artifacts())));}
        return out.stream().sorted(Comparator.comparingLong(Hint::distanceSquared).thenComparing(Hint::site)).limit(10).toList();
    }
}
