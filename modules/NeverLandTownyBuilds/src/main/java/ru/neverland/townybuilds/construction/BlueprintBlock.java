package ru.neverland.townybuilds.construction;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;

public record BlueprintBlock(Material material, BlockRole role, int stage,
                             BlockFace facing, Axis axis,
                             Bisected.Half half, Door.Hinge hinge) {
    public BlueprintBlock(Material material, BlockRole role, int stage) {
        this(material, role, stage, BlockFace.NORTH, Axis.Y, null, null);
    }

    public BlueprintBlock {
        if (material == null || material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
            throw new IllegalArgumentException("Материал чертежа не может быть воздухом");
        }
        if (role == null) throw new IllegalArgumentException("Не указана роль блока чертежа");
        if (stage < 1 || stage > 5) throw new IllegalArgumentException("Этап должен находиться в диапазоне 1–5");
    }

    public BlueprintBlock withAxis(Axis value) {
        return new BlueprintBlock(material, role, stage, facing, value, half, hinge);
    }

    public BlueprintBlock withFacing(BlockFace value) {
        return new BlueprintBlock(material, role, stage, value, axis, half, hinge);
    }

    public BlueprintBlock asStair(BlockFace value, Bisected.Half stairHalf) {
        return new BlueprintBlock(material, role, stage, value, axis, stairHalf, null);
    }

    public BlueprintBlock asDoor(Bisected.Half value, Door.Hinge doorHinge) {
        return new BlueprintBlock(material, role, stage, BlockFace.NORTH, Axis.Y, value, doorHinge);
    }

    public BlueprintBlock asDoor(BlockFace doorFacing, Bisected.Half value, Door.Hinge doorHinge) {
        return new BlueprintBlock(material, role, stage, doorFacing, Axis.Y, value, doorHinge);
    }
}
