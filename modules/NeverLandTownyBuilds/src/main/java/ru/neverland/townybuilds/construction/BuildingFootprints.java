package ru.neverland.townybuilds.construction;
import ru.neverland.townybuilds.api.BuildingFootprint;
import java.util.*;

/** Cache blueprint bounds, not world blocks; never load chunks to determine districts. */
public final class BuildingFootprints {
    private final BuildingBlueprintGenerator generator=new BuildingBlueprintGenerator();
    private final Map<String,int[]> bounds=new HashMap<>();
    public Optional<BuildingFootprint> footprint(ConstructionSite site,int savedLevel){
        int required=generator.maximumStage(site.projectId());if(required==0)return Optional.empty();
        String key=site.projectId()+":"+site.architectureVersion();
        int[] local=bounds.computeIfAbsent(key,k->{
            var plan=generator.generateForArchitecture(site.projectId(),required,site.architectureVersion());
            if(plan==null||plan.blocks().isEmpty())return null;
            int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
            for(var offset:plan.blocks().keySet()){minX=Math.min(minX,offset.x());minZ=Math.min(minZ,offset.z());maxX=Math.max(maxX,offset.x());maxZ=Math.max(maxZ,offset.z());}
            return new int[]{minX,minZ,maxX,maxZ};
        });
        if(local==null)return Optional.empty();
        var first=site.location(null,new BlockOffset(local[0],0,local[1]));var second=site.location(null,new BlockOffset(local[2],0,local[3]));
        return Optional.of(new BuildingFootprint(site.worldId(),Math.min(first.getBlockX(),second.getBlockX()),Math.min(first.getBlockZ(),second.getBlockZ()),
                Math.max(first.getBlockX(),second.getBlockX()),Math.max(first.getBlockZ(),second.getBlockZ()),Math.min(savedLevel,site.completedStage()),required));
    }
}
