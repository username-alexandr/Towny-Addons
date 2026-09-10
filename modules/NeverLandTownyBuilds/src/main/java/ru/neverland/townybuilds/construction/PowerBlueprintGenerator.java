package ru.neverland.townybuilds.construction;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import java.util.*;
/** Three additive, five-stage power plants. All roofs rest on completed walls. */
public final class PowerBlueprintGenerator {
    public static final Set<String> PROJECTS=Set.of("water_wheel","generator","power_station");
    public BlueprintPlan generate(String id,int requested){
        if(!PROJECTS.contains(id))return null;int level=Math.max(1,Math.min(5,requested));var b=new Builder(level);
        switch(id){case "water_wheel"->wheel(b);case "generator"->generator(b);case "power_station"->station(b);default->throw new IllegalArgumentException(id);}
        return new BlueprintPlan(id,level,stageName(level),b.blocks);
    }
    public String stageName(int level){return List.of("Фундамент и площадка","Несущий корпус","Крыша и энергоузел","Расширение мощности","Городская энергосистема").get(Math.max(1,Math.min(5,level))-1);}
    private void house(Builder b,int hx,int hz,Material wall,Material roof){
        b.floor(-hx-1,hx+1,-hz-1,hz+1,0,Material.STONE_BRICKS,1);b.floor(-1,1,-hz-3,-hz-2,0,Material.STONE_BRICKS,1);
        for(int y=1;y<=4;y++){
            for(int x=-hx;x<=hx;x++){if(x!=0||y>2)b.block(x,y,-hz,wall,2);b.block(x,y,hz,wall,2);}
            for(int z=-hz+1;z<hz;z++){b.block(-hx,y,z,wall,2);b.block(hx,y,z,wall,2);}
        }
        b.floor(-hx-1,hx+1,-hz-1,hz+1,5,roof,3);b.door(0,1,-hz,Material.SPRUCE_DOOR,3);
        b.block(-hx+1,1,-hz+1,Material.BARREL,3);b.block(hx-1,1,-hz+1,Material.LANTERN,3);
    }
    private void wheel(Builder b){
        house(b,4,4,Material.STRIPPED_SPRUCE_LOG,Material.SPRUCE_PLANKS);
        // Closed stone trough: the decorative water cannot escape through an open end.
        b.floor(6,8,-5,5,0,Material.STONE_BRICKS,1);
        for(int z=-5;z<=5;z++){b.block(6,1,z,Material.STONE_BRICKS,2);b.block(8,1,z,Material.STONE_BRICKS,2);}
        b.block(7,1,-5,Material.STONE_BRICKS,2);b.block(7,1,5,Material.STONE_BRICKS,2);
        for(int z=-4;z<=4;z++)b.block(7,1,z,Material.WATER,3);
        for(int x=4;x<=7;x++)b.block(x,5,0,Material.STRIPPED_SPRUCE_LOG,3,Axis.X);
        // Wheel in a vertical Y/Z plane, with cross-shaped spokes on the axle.
        for(int dy=-3;dy<=3;dy++)for(int z=-3;z<=3;z++){
            int d=dy*dy+z*z;if(d>=5&&d<=11)b.block(7,5+dy,z,Material.SPRUCE_PLANKS,3);
            else if((dy==0||z==0)&&Math.abs(dy)+Math.abs(z)<=2)b.block(7,5+dy,z,Material.STRIPPED_SPRUCE_LOG,3);
        }
        b.floor(-3,-2,2,3,1,Material.CUT_COPPER,4);b.block(-3,2,3,Material.LIGHTNING_ROD,4);
        b.floor(-5,5,6,8,0,Material.STONE_BRICKS,5);for(int x:new int[]{-4,4}){b.block(x,1,7,Material.SPRUCE_FENCE,5);b.block(x,2,7,Material.LANTERN,5);}
    }
    private void generator(Builder b){
        house(b,5,4,Material.BRICKS,Material.CUT_COPPER);
        b.floor(-3,-2,0,2,1,Material.IRON_BLOCK,3);b.floor(2,3,0,2,1,Material.CUT_COPPER,3);
        for(int z=0;z<=2;z++){b.block(-3,2,z,Material.BLAST_FURNACE,3);b.block(3,2,z,Material.COPPER_BLOCK,3);}
        for(int x:new int[]{-2,2})b.block(x,2,2,Material.IRON_BLOCK,4);
        for(int x=-2;x<=2;x++)b.block(x,3,2,Material.IRON_BLOCK,4);
        b.floor(-1,1,6,8,0,Material.STONE_BRICKS,4);for(int y=1;y<=8;y++)b.block(0,y,7,Material.POLISHED_BASALT,4);b.block(0,9,7,Material.LIGHTNING_ROD,4);
        for(int x:new int[]{-4,4}){b.block(x,0,6,Material.STONE_BRICKS,5);b.block(x,1,6,Material.STONE_BRICKS,5);b.block(x,2,6,Material.COPPER_BLOCK,5);b.block(x,3,6,Material.LIGHTNING_ROD,5);}
    }
    private void station(Builder b){
        house(b,8,6,Material.DEEPSLATE_BRICKS,Material.SMOOTH_STONE);
        for(int x:new int[]{-5,5}){b.floor(x-1,x+1,0,3,1,Material.IRON_BLOCK,3);b.floor(x-1,x+1,0,3,2,Material.CUT_COPPER,3);b.block(x,3,2,Material.LIGHTNING_ROD,3);}
        b.floor(-7,7,8,12,0,Material.STONE_BRICKS,4);
        for(int x:new int[]{-5,5})for(int y=1;y<=12;y++){b.block(x,y,10,Material.BRICKS,4);b.block(x+1,y,10,Material.BRICKS,4);}
        for(int x:new int[]{-5,5}){b.block(x,13,10,Material.LIGHTNING_ROD,4);b.block(x+1,13,10,Material.LIGHTNING_ROD,4);}
        b.floor(-8,8,-10,-8,0,Material.STONE_BRICKS,5);
        for(int x:new int[]{-6,-3,3,6}){b.block(x,1,-9,Material.COPPER_BLOCK,5);b.block(x,2,-9,Material.IRON_BLOCK,5);b.block(x,3,-9,Material.LIGHTNING_ROD,5);}
        for(int z=-4;z<=4;z+=4){b.block(-7,1,z,Material.REDSTONE_LAMP,5);b.block(7,1,z,Material.REDSTONE_LAMP,5);}
    }
    private static final class Builder {
        final int level;final Map<BlockOffset,BlueprintBlock> blocks=new LinkedHashMap<>();Builder(int level){this.level=level;}
        void block(int x,int y,int z,Material m,int stage){block(x,y,z,m,stage,Axis.Y);}
        void block(int x,int y,int z,Material m,int stage,Axis axis){if(stage<=level)blocks.putIfAbsent(new BlockOffset(x,y,z),new BlueprintBlock(m,m==Material.WATER?BlockRole.DECORATION:BlockRole.RESIDENT,stage).withAxis(axis));}
        void floor(int x0,int x1,int z0,int z1,int y,Material m,int stage){for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)block(x,y,z,m,stage);}
        void door(int x,int y,int z,Material m,int stage){if(stage>level)return;blocks.put(new BlockOffset(x,y,z),new BlueprintBlock(m,BlockRole.RESIDENT,stage).asDoor(BlockFace.NORTH,Bisected.Half.BOTTOM,Door.Hinge.LEFT));blocks.put(new BlockOffset(x,y+1,z),new BlueprintBlock(m,BlockRole.RESIDENT,stage).asDoor(BlockFace.NORTH,Bisected.Half.TOP,Door.Hinge.LEFT));}
    }
}
