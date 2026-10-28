package ru.neverland.townybuilds.construction;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;

public record BlueprintBlock(Material material, BlockRole role, int stage,
                             BlockFace facing, Axis axis,
                             Bisected.Half half, Door.Hinge hinge,String blockData) {
    public BlueprintBlock(Material material,BlockRole role,int stage,BlockFace facing,Axis axis,Bisected.Half half,Door.Hinge hinge){this(material,role,stage,facing,axis,half,hinge,null);}
    public BlueprintBlock(Material material, BlockRole role, int stage) {
        this(material, role, stage, BlockFace.NORTH, Axis.Y, null, null);
    }

    public BlueprintBlock {
        if (material == null || material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
            throw new IllegalArgumentException("Материал чертежа не может быть воздухом");
        }
        if (role == null) throw new IllegalArgumentException("Не указана роль блока чертежа");
        if (stage < 1 || stage > 5) throw new IllegalArgumentException("Этап должен находиться в диапазоне 1–5");
        if(blockData!=null&&(!blockData.matches("minecraft:"+material.name().toLowerCase(java.util.Locale.ROOT)+"(?:\\[[a-z0-9_=,]+\\])?")||blockData.length()>512))throw new IllegalArgumentException("Состояние блока не соответствует материалу");
    }

    public BlueprintBlock withAxis(Axis value) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        return new BlueprintBlock(material, role, stage, facing, value, half, hinge,state("axis",value));
    } }

    public BlueprintBlock withFacing(BlockFace value) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        return new BlueprintBlock(material, role, stage, value, axis, half, hinge,state("facing",value));
    } }

    public BlueprintBlock asStair(BlockFace value, Bisected.Half stairHalf) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        return new BlueprintBlock(material, role, stage, value, axis, stairHalf, null,state("facing",value,"half",stairHalf));
    } }

    public BlueprintBlock asDoor(Bisected.Half value, Door.Hinge doorHinge) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        return new BlueprintBlock(material, role, stage, BlockFace.NORTH, Axis.Y, value, doorHinge,state("facing",BlockFace.NORTH,"half",value,"hinge",doorHinge));
    } }

    public BlueprintBlock asDoor(BlockFace doorFacing, Bisected.Half value, Door.Hinge doorHinge) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        return new BlueprintBlock(material, role, stage, doorFacing, Axis.Y, value, doorHinge,state("facing",doorFacing,"half",value,"hinge",doorHinge));
    } }
    private String state(Object... pairs){String result=blockData;if(result!=null)for(int n=0;n<pairs.length;n+=2)if(pairs[n+1]!=null)result=result.replaceAll("([\\[,])"+pairs[n]+"=[^,\\]]+","$1"+pairs[n]+"="+pairs[n+1].toString().toLowerCase(java.util.Locale.ROOT));return result;}
}
