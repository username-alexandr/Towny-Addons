package ru.neverland.townybuilds.construction;
import org.bukkit.block.BlockFace;
public final class PlacementNudgeSmoke {
    public static void main(String[] args){
        for(var face:new BlockFace[]{BlockFace.NORTH,BlockFace.EAST,BlockFace.SOUTH,BlockFace.WEST}){
            var front=PlacementNudge.of("forward",face);var back=PlacementNudge.of("back",face);var left=PlacementNudge.of("left",face);var right=PlacementNudge.of("right",face);
            if(front.x()!=face.getModX()||front.z()!=face.getModZ()||front.x()!=-back.x()||front.z()!=-back.z())throw new AssertionError("forward/back "+face);
            if(left.x()!=-right.x()||left.z()!=-right.z()||front.x()*left.x()+front.z()*left.z()!=0)throw new AssertionError("left/right "+face);
            if(PlacementNudge.of("up",face).y()!=1||PlacementNudge.of("down",face).y()!=-1)throw new AssertionError("vertical "+face);
        }
        for(String action:new String[]{"confirm","rotate","1000,0,0",""})try{PlacementNudge.of(action,BlockFace.NORTH);throw new AssertionError("accepted "+action);}catch(IllegalArgumentException expected){}
        System.out.println("PlacementNudgeSmoke OK: cardinal directions and action allowlist");
    }
}
