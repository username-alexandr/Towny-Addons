package ru.neverland.mintcamps.service;

import org.bukkit.Material;
import ru.neverland.mintcamps.model.BlockPos;
import ru.neverland.mintcamps.model.Placement;
import ru.neverland.mintcamps.model.PlacementKind;
import ru.neverland.mintcamps.model.StyleDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StructureGenerator {
    public static final BlockPos CAMPFIRE_RELATIVE = new BlockPos(0, 0, 0);

    public List<Placement> generate(int level, StyleDefinition style) {
        Builder builder = new Builder();
        addCommon(builder, style);
        switch (Math.max(1, Math.min(3, level))) {
            case 1 -> levelOne(builder, style);
            case 2 -> levelTwo(builder, style);
            case 3 -> levelThree(builder, style);
            default -> throw new IllegalStateException();
        }
        return builder.values();
    }

    public BlockPos stashRelative(int level) {
        return switch (Math.max(1, Math.min(3, level))) {
            case 1 -> new BlockPos(1, 1, 5);
            case 2 -> new BlockPos(2, 1, 6);
            default -> new BlockPos(3, 1, 7);
        };
    }

    private void addCommon(Builder b, StyleDefinition style) {
        b.add(0, 0, 0, Material.CAMPFIRE, PlacementKind.CAMPFIRE);
        b.add(-1, 0, 0, style.material("stone-ring"), PlacementKind.SLAB_BOTTOM);
        b.add(1, 0, 0, style.material("stone-ring"), PlacementKind.SLAB_BOTTOM);
        b.add(0, 0, -1, style.material("stone-ring"), PlacementKind.SLAB_BOTTOM);
        b.add(0, 0, 1, style.material("stone-ring"), PlacementKind.SLAB_BOTTOM);
    }

    private void levelOne(Builder b, StyleDefinition s) {
        // Палатка 3x3 и настил.
        for (int x = -1; x <= 1; x++) for (int z = 3; z <= 5; z++) b.add(x, 0, z, s.material("planks"));
        for (int z = 3; z <= 5; z++) {
            b.add(-1, 1, z, s.material("wool"));
            b.add(1, 1, z, s.material("wool"));
            b.add(-1, 2, z, s.material("wool"));
            b.add(1, 2, z, s.material("wool"));
            b.add(0, 3, z, s.material("wool"));
        }
        b.add(-1, 1, 5, s.material("wool"));
        b.add(0, 1, 5, s.material("carpet"));
        b.add(1, 1, 5, Material.BARREL, PlacementKind.BACKWARD);
        b.add(0, 1, 4, s.material("carpet"));
        // Пенёк для отдыха.
        b.add(-2, 1, 0, s.material("stripped-log"), PlacementKind.LOG_VERTICAL);
    }

    private void levelTwo(Builder b, StyleDefinition s) {
        // Двускатная палатка 5x4.
        for (int x = -2; x <= 2; x++) for (int z = 3; z <= 6; z++) b.add(x, 0, z, s.material("planks"));
        for (int z = 3; z <= 6; z++) {
            for (int y = 1; y <= 2; y++) {
                b.add(-2, y, z, s.material("wool"));
                b.add(2, y, z, s.material("wool"));
            }
            b.add(-2, 3, z, s.material("wool"));
            b.add(2, 3, z, s.material("wool"));
            b.add(-1, 4, z, s.material("wool"));
            b.add(1, 4, z, s.material("wool"));
            b.add(0, 5, z, s.material("wool"));
        }
        for (int x = -2; x <= 2; x++) {
            if (x != 0) b.add(x, 1, 6, s.material("wool"));
        }
        b.add(-2, 1, 4, Material.SMOKER, PlacementKind.BACKWARD);
        b.add(2, 1, 4, Material.CRAFTING_TABLE);
        b.add(2, 1, 6, Material.BARREL, PlacementKind.BACKWARD);
        addDoubleBed(b, s, 4, 5);
        // Четыре факельных столба.
        post(b, s, -4, 2, 2);
        post(b, s, 4, 2, 2);
        post(b, s, -4, 8, 2);
        post(b, s, 4, 8, 2);
    }

    private void levelThree(Builder b, StyleDefinition s) {
        // Шатёр-павильон 7x5 на каменном фундаменте.
        for (int x = -3; x <= 3; x++) for (int z = 3; z <= 7; z++) {
            boolean edge = x == -3 || x == 3 || z == 3 || z == 7;
            b.add(x, 0, z, edge ? s.material("foundation") : s.material("planks"));
        }
        int[][] corners = {{-3, 3}, {3, 3}, {-3, 7}, {3, 7}};
        for (int[] corner : corners) for (int y = 1; y <= 5; y++) {
            b.add(corner[0], y, corner[1], s.material("log"), PlacementKind.LOG_VERTICAL);
        }
        for (int z = 3; z <= 7; z++) {
            b.add(-3, 5, z, s.material("wool"));
            b.add(3, 5, z, s.material("wool"));
            b.add(-2, 6, z, s.material("wool"));
            b.add(2, 6, z, s.material("wool"));
            b.add(-1, 7, z, s.material("wool"));
            b.add(0, 7, z, s.material("wool"));
            b.add(1, 7, z, s.material("wool"));
        }
        // Функциональная зона и подвесная люстра.
        b.add(-2, 1, 6, Material.BLAST_FURNACE, PlacementKind.BACKWARD);
        b.add(-1, 1, 6, Material.FURNACE, PlacementKind.BACKWARD);
        b.add(2, 1, 6, Material.ANVIL, PlacementKind.RIGHT);
        b.add(3, 1, 7, Material.BARREL, PlacementKind.BACKWARD);
        b.add(0, 6, 5, Material.IRON_BARS);
        b.add(0, 5, 5, Material.IRON_BARS);
        b.add(0, 4, 5, s.material("light"), PlacementKind.LANTERN_HANGING);

        // Четыре палисадные смотровые башни.
        tower(b, s, -7, 2);
        tower(b, s, 7, 2);
        tower(b, s, -7, 9);
        tower(b, s, 7, 9);
        palisade(b, s);

        // Флагшток и знамя.
        for (int y = 1; y <= 7; y++) b.add(0, y, 11, s.material("log"), PlacementKind.LOG_VERTICAL);
        b.add(0, 8, 11, s.material("banner"), PlacementKind.BANNER_FORWARD);
    }

    private void addDoubleBed(Builder b, StyleDefinition s, int footZ, int headZ) {
        Material bed = s.material("bed");
        if (bed.name().endsWith("_BED")) {
            for (int x : new int[]{-1, 1}) {
                b.add(x, 1, footZ, bed, PlacementKind.BED_FOOT_FORWARD);
                b.add(x, 1, headZ, bed, PlacementKind.BED_HEAD_FORWARD);
            }
        } else {
            for (int x : new int[]{-1, 1}) for (int z = footZ; z <= headZ; z++) b.add(x, 1, z, bed);
        }
    }

    private void post(Builder b, StyleDefinition s, int x, int z, int height) {
        b.add(x, 0, z, s.material("foundation"));
        for (int y = 1; y <= height; y++) b.add(x, y, z, s.material("fence"));
        b.add(x, height + 1, z, Material.TORCH);
    }

    private void tower(Builder b, StyleDefinition s, int centerX, int centerZ) {
        for (int x = centerX - 1; x <= centerX + 1; x++) for (int z = centerZ - 1; z <= centerZ + 1; z++) {
            b.add(x, 0, z, s.material("foundation"));
            b.add(x, 5, z, s.material("planks"));
        }
        for (int dx : new int[]{-1, 1}) for (int dz : new int[]{-1, 1}) {
            for (int y = 1; y <= 5; y++) b.add(centerX + dx, y, centerZ + dz, s.material("log"), PlacementKind.LOG_VERTICAL);
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (Math.abs(dx) == 1 || Math.abs(dz) == 1) b.add(centerX + dx, 6, centerZ + dz, s.material("fence"));
        }
        b.add(centerX, 6, centerZ, s.material("light"));
        b.add(centerX, 7, centerZ, s.material("light"), PlacementKind.LANTERN_HANGING);
    }

    private void palisade(Builder b, StyleDefinition s) {
        for (int x = -5; x <= 5; x++) if (Math.abs(x) > 1) {
            b.add(x, 0, 2, s.material("foundation"));
            b.add(x, 0, 9, s.material("foundation"));
            for (int y = 1; y <= 3; y++) {
                b.add(x, y, 2, s.material("fence"));
                b.add(x, y, 9, s.material("fence"));
            }
        }
        for (int z = 4; z <= 7; z++) {
            b.add(-7, 0, z, s.material("foundation"));
            b.add(7, 0, z, s.material("foundation"));
            for (int y = 1; y <= 3; y++) {
                b.add(-7, y, z, s.material("fence"));
                b.add(7, y, z, s.material("fence"));
            }
        }
    }

    private static final class Builder {
        private final Map<BlockPos, Placement> placements = new LinkedHashMap<>();

        void add(int x, int y, int z, Material material) {
            add(x, y, z, material, PlacementKind.DEFAULT);
        }

        void add(int x, int y, int z, Material material, PlacementKind kind) {
            BlockPos position = new BlockPos(x, y, z);
            placements.put(position, new Placement(position, material, kind));
        }

        List<Placement> values() {
            return new ArrayList<>(placements.values());
        }
    }
}
