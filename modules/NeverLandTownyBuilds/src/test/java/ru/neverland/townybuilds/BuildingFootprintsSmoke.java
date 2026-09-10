package ru.neverland.townybuilds;
import org.bukkit.block.BlockFace;
import ru.neverland.townybuilds.construction.*;
import java.util.UUID;
public final class BuildingFootprintsSmoke {
    public static void main(String[] args){
        var footprints=new BuildingFootprints();var generator=new BuildingBlueprintGenerator();UUID world=UUID.randomUUID();int count=0;
        for(String id:generator.supportedProjects())for(BlockFace face:new BlockFace[]{BlockFace.NORTH,BlockFace.EAST,BlockFace.SOUTH,BlockFace.WEST}){
            int max=generator.maximumStage(id);var site=new ConstructionSite(id,world,32,60,-16,face,max,max,max,false);
            var footprint=footprints.footprint(site,max).orElseThrow();var plan=generator.generate(id,max);
            check(footprint.requiredLevel()==max&&footprint.completedLevel()==max&&footprint.worldId().equals(world),"metadata");
            for(var offset:plan.blocks().keySet()){
                var location=site.location(null,offset);
                check(location.getBlockX()>=footprint.minX()&&location.getBlockX()<=footprint.maxX()
                    &&location.getBlockZ()>=footprint.minZ()&&location.getBlockZ()<=footprint.maxZ(),id+" rotated block outside footprint");
            }
            count++;
        }
        var north=new ConstructionSite("residential_quarter",world,32,60,-16,BlockFace.NORTH,4,5,5,true);
        var bounds=footprints.footprint(north,5).orElseThrow();
        check(bounds.minX()==21&&bounds.maxX()==43&&bounds.minZ()==-25&&bounds.maxZ()==-3,"courtyard and porch included");
        check(bounds.completedLevel()==4,"saved level cannot activate unfinished construction");
        System.out.println("BuildingFootprintsSmoke OK: "+count+" complete, rotated footprints; courtyard and completion gates");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
