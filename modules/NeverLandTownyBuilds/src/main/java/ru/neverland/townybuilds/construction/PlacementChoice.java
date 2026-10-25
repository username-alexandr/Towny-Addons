package ru.neverland.townybuilds.construction;
import java.util.UUID;
import org.bukkit.block.BlockFace;
public record PlacementChoice(UUID world,int x,int y,int z,BlockFace facing) {
    public PlacementChoice {if(world==null||facing==null)throw new IllegalArgumentException("Укажите место здания");facing=ConstructionSite.cardinal(facing);}
    public PlacementChoice move(int dx,int dy,int dz){return new PlacementChoice(world,Math.addExact(x,dx),Math.addExact(y,dy),Math.addExact(z,dz),facing);}
    public PlacementChoice rotate(){return new PlacementChoice(world,x,y,z,switch(facing){case NORTH->BlockFace.EAST;case EAST->BlockFace.SOUTH;case SOUTH->BlockFace.WEST;default->BlockFace.NORTH;});}
}
