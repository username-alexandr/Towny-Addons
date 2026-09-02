package ru.neverland.mintcamps.model;

import org.bukkit.Material;

public record Placement(BlockPos relative, Material material, PlacementKind kind) {
    public Placement(int x, int y, int z, Material material) {
        this(new BlockPos(x, y, z), material, PlacementKind.DEFAULT);
    }

    public Placement(int x, int y, int z, Material material, PlacementKind kind) {
        this(new BlockPos(x, y, z), material, kind);
    }
}
