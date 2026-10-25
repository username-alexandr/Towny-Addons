package ru.neverland.townybuilds.construction;
import org.bukkit.block.BlockFace;
/** Relative directions are resolved by the server; client coordinates are never accepted. */
public record PlacementNudge(int x,int y,int z) {
    public static PlacementNudge of(String action,BlockFace facing){
        BlockFace f=ConstructionSite.cardinal(facing);int x=f.getModX(),z=f.getModZ();
        return switch(action){
            case "forward"->new PlacementNudge(x,0,z);
            case "back"->new PlacementNudge(-x,0,-z);
            case "left"->new PlacementNudge(z,0,-x);
            case "right"->new PlacementNudge(-z,0,x);
            case "up"->new PlacementNudge(0,1,0);
            case "down"->new PlacementNudge(0,-1,0);
            default->throw new IllegalArgumentException("Неизвестное направление");
        };
    }
}
