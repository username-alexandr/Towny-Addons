package ru.neverland.townybuilds.construction;

import org.bukkit.Material;
import java.util.*;

public final class ConstructionLayersSmoke {
    static int checks;
    static void ok(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
    public static void main(String[] args) {
        Map<BlockOffset,BlueprintBlock> cells=new HashMap<>();
        var a=new BlockOffset(0,-2,0);var b=new BlockOffset(1,-2,0);var c=new BlockOffset(0,3,0);var d=new BlockOffset(0,7,0);
        cells.put(a,new BlueprintBlock(Material.STONE,BlockRole.RESIDENT,1));cells.put(b,cells.get(a));
        cells.put(c,new BlueprintBlock(Material.OAK_PLANKS,BlockRole.RESIDENT,1));
        cells.put(d,new BlueprintBlock(Material.OAK_PLANKS,BlockRole.RESIDENT,2));
        cells.put(new BlockOffset(2,-8,2),new BlueprintBlock(Material.LANTERN,BlockRole.DECORATION,1));
        var plan=new BlueprintPlan("test",2,"test",cells);Set<BlockOffset> placed=new HashSet<>();
        var first=ConstructionLayers.inspect(plan,1,1,(p,v)->placed.contains(p));
        ok(first.y()==-2&&first.number()==1&&first.layers()==2,"negative foundations, gaps and target stage");
        ok(first.missing().size()==2&&first.placed()==0,"only first unfinished layer");
        placed.add(a);var partial=ConstructionLayers.inspect(plan,1,1,(p,v)->placed.contains(p));
        ok(partial.number()==1&&partial.placed()==1&&partial.total()==2,"partial layer cannot advance");
        placed.add(b);var second=ConstructionLayers.inspect(plan,1,1,(p,v)->placed.contains(p));
        ok(second.y()==3&&second.number()==2&&second.missing().get(0).offset().equals(c),"next layer opens only when complete");
        var restart=ConstructionLayers.inspect(plan,1,1,(p,v)->placed.contains(p));
        ok(second.equals(restart),"restart derives identical progress from blocks");
        placed.remove(a);ok(ConstructionLayers.inspect(plan,1,1,(p,v)->placed.contains(p)).number()==1,"damage returns to earliest layer");
        placed.add(a);placed.add(c);ok(ConstructionLayers.inspect(plan,1,1,(p,v)->placed.contains(p)).complete(),"stage completes without decoration");
        ok(ConstructionLayers.inspect(plan,2,2,(p,v)->false).missing().size()==1,"upgrades do not ask to rebuild old levels");
        System.out.println("ConstructionLayersSmoke: "+checks+" checks passed");
    }
}
