package ru.neverland.townybuilds;
import org.bukkit.Material;
import ru.neverland.townybuilds.construction.*;
import java.util.*;
public final class PowerBlueprintSmoke {
    private static void check(boolean b,String text){if(!b)throw new AssertionError(text);}
    public static void main(String[] args){var generator=new BuildingBlueprintGenerator();int stages=0;
        for(String id:PowerBlueprintGenerator.PROJECTS){Map<BlockOffset,BlueprintBlock> previous=Map.of();for(int stage=1;stage<=5;stage++){
            var plan=generator.generate(id,stage);var blocks=plan.blocks();check(blocks.size()>previous.size(),"every stage adds physical blocks: "+id);for(var e:previous.entrySet())check(e.getValue().equals(blocks.get(e.getKey())),"upgrade must preserve earlier blocks: "+id+e.getKey());
            for(var e:blocks.entrySet())check(e.getValue().stage()<=stage,"no future block");
            int hx=id.equals("water_wheel")?4:id.equals("generator")?5:8,hz=id.equals("power_station")?6:4;
            if(stage>=3){for(int y=1;y<=4;y++){for(int z=-hz;z<=hz;z++)for(int x:new int[]{-hx,hx})check(blocks.containsKey(new BlockOffset(x,y,z)),"side walls support roof");for(int x=-hx;x<=hx;x++)for(int z:new int[]{-hz,hz})check(blocks.containsKey(new BlockOffset(x,y,z)),"closed gables and door");}for(int x=-hx-1;x<=hx+1;x++)for(int z=-hz-1;z<=hz+1;z++)check(blocks.containsKey(new BlockOffset(x,5,z)),"continuous roof");}
            // Every structural component has a face-connected path down to the foundation.
            Set<BlockOffset> reached=new HashSet<>();ArrayDeque<BlockOffset> queue=new ArrayDeque<>();for(var e:blocks.entrySet())if(e.getKey().y()==0&&e.getValue().role()==BlockRole.RESIDENT){reached.add(e.getKey());queue.add(e.getKey());}
            int[][] directions={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
            while(!queue.isEmpty()){var p=queue.remove();for(var d:directions){var next=new BlockOffset(p.x()+d[0],p.y()+d[1],p.z()+d[2]);var b=blocks.get(next);if(b!=null&&b.role()==BlockRole.RESIDENT&&reached.add(next))queue.add(next);}}
            for(var e:blocks.entrySet())if(e.getValue().role()==BlockRole.RESIDENT)check(reached.contains(e.getKey()),"floating structure: "+id+" stage "+stage+" "+e.getKey());
            previous=blocks;stages++;
        }}
        var wheel=generator.generate("water_wheel",3).blocks();for(int z=-4;z<=4;z++){check(wheel.get(new BlockOffset(7,1,z)).material()==Material.WATER,"contained water trough");for(int x:new int[]{6,8})check(wheel.get(new BlockOffset(x,1,z)).material()==Material.STONE_BRICKS,"trough side");}check(wheel.get(new BlockOffset(7,2,0)).material()==Material.SPRUCE_PLANKS,"lower paddle is not overwritten by water");
        System.out.println("PowerBlueprintSmoke OK: "+stages+" additive stages, closed supported roofs, foundation-connected machinery and contained water");
    }
}
