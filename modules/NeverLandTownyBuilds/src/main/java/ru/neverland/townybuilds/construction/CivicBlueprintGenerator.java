package ru.neverland.townybuilds.construction;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Физические пятиэтапные модели муниципальной очереди 0.6.0. */
public final class CivicBlueprintGenerator {
    public static final List<String> PROJECTS = List.of(
            "printing_house", "forestry", "customs", "trade_port", "mint", "stables",
            "fortress_wall", "city_moat", "archery_range", "port_fort", "census_bureau",
            "insurance_chamber", "pumping_station", "irrigation_station", "recycling_yard", "dam"
    );

    public static final Set<String> AREA_PROJECTS = Set.of("forestry", "irrigation_station");
    public static final Set<String> LINE_PROJECTS = Set.of("fortress_wall", "city_moat", "dam");

    private static final Map<String, Profile> PROFILES = Map.ofEntries(
            Map.entry("printing_house", new Profile(4, 4, Material.BRICKS, Material.WHITE_TERRACOTTA,
                    Material.DARK_OAK_LOG, Material.DEEPSLATE_TILES, Material.DARK_OAK_DOOR, Material.LECTERN)),
            Map.entry("forestry", new Profile(5, 4, Material.MOSSY_COBBLESTONE, Material.OAK_PLANKS,
                    Material.OAK_LOG, Material.OAK_PLANKS, Material.OAK_DOOR, Material.COMPOSTER)),
            Map.entry("customs", new Profile(4, 5, Material.STONE_BRICKS, Material.CUT_COPPER,
                    Material.STRIPPED_SPRUCE_LOG, Material.DEEPSLATE_TILES, Material.SPRUCE_DOOR, Material.BARREL)),
            Map.entry("trade_port", new Profile(6, 4, Material.STONE_BRICKS, Material.DARK_PRISMARINE,
                    Material.STRIPPED_DARK_OAK_LOG, Material.DARK_PRISMARINE, Material.DARK_OAK_DOOR, Material.BARREL)),
            Map.entry("mint", new Profile(4, 6, Material.SMOOTH_STONE, Material.SMOOTH_QUARTZ,
                    Material.QUARTZ_PILLAR, Material.CUT_COPPER, Material.IRON_DOOR, Material.SMITHING_TABLE)),
            Map.entry("stables", new Profile(6, 5, Material.COBBLESTONE, Material.OAK_PLANKS,
                    Material.STRIPPED_OAK_LOG, Material.SPRUCE_PLANKS, Material.OAK_DOOR, Material.HAY_BLOCK)),
            Map.entry("archery_range", new Profile(5, 5, Material.COBBLESTONE, Material.SPRUCE_PLANKS,
                    Material.SPRUCE_LOG, Material.SPRUCE_PLANKS, Material.SPRUCE_DOOR, Material.FLETCHING_TABLE)),
            Map.entry("port_fort", new Profile(6, 6, Material.DEEPSLATE_BRICKS, Material.STONE_BRICKS,
                    Material.POLISHED_BASALT, Material.DEEPSLATE_TILES, Material.IRON_DOOR, Material.CROSSBOW)),
            Map.entry("census_bureau", new Profile(5, 6, Material.STONE_BRICKS, Material.CALCITE,
                    Material.STRIPPED_BIRCH_LOG, Material.DEEPSLATE_TILES, Material.BIRCH_DOOR, Material.WRITABLE_BOOK)),
            Map.entry("insurance_chamber", new Profile(4, 7, Material.POLISHED_ANDESITE, Material.SMOOTH_QUARTZ,
                    Material.QUARTZ_PILLAR, Material.CUT_COPPER, Material.IRON_DOOR, Material.ENDER_CHEST)),
            Map.entry("pumping_station", new Profile(7, 4, Material.BRICKS, Material.COPPER_BLOCK,
                    Material.IRON_BLOCK, Material.CUT_COPPER, Material.IRON_DOOR, Material.CAULDRON)),
            Map.entry("irrigation_station", new Profile(7, 5, Material.MUD_BRICKS, Material.OAK_PLANKS,
                    Material.STRIPPED_OAK_LOG, Material.OAK_PLANKS, Material.OAK_DOOR, Material.WATER_BUCKET)),
            Map.entry("recycling_yard", new Profile(7, 6, Material.STONE_BRICKS, Material.IRON_BLOCK,
                    Material.STRIPPED_DARK_OAK_LOG, Material.CUT_COPPER, Material.IRON_DOOR, Material.CRAFTER))
    );

    public Set<String> supportedProjects() {
        return Set.copyOf(PROJECTS);
    }

    public boolean isArea(String projectId) {
        return projectId != null && AREA_PROJECTS.contains(projectId.toLowerCase(Locale.ROOT));
    }

    public boolean isLinear(String projectId) {
        return projectId != null && LINE_PROJECTS.contains(projectId.toLowerCase(Locale.ROOT));
    }

    public BlueprintPlan generate(String rawId, int rawLevel) {
        String id = rawId == null ? "" : rawId.toLowerCase(Locale.ROOT);
        if (!PROJECTS.contains(id)) return null;
        int level = Math.max(1, Math.min(5, rawLevel));
        Builder builder = new Builder(level);
        if (LINE_PROJECTS.contains(id)) {
            switch (id) {
                case "fortress_wall" -> fortressWall(builder);
                case "city_moat" -> cityMoat(builder);
                case "dam" -> dam(builder);
                default -> throw new IllegalStateException("Неизвестная линейная модель " + id);
            }
        } else {
            civicHouse(builder, PROFILES.get(id), PROJECTS.indexOf(id));
        }
        return new BlueprintPlan(id, level, stageName(id, level), builder.blocks);
    }

    public String stageName(String id, int rawLevel) {
        int level = Math.max(1, Math.min(5, rawLevel));
        String[][] common = {
                {"Площадка и фундамент", "Служебный корпус", "Крыша и проходимый вход", "Рабочее оснащение", "Полная городская отделка"},
                {"Разметка участка", "Несущая линия", "Защитный профиль", "Служебные узлы", "Завершённый маршрут"}
        };
        return common[LINE_PROJECTS.contains(id) ? 1 : 0][level - 1];
    }

    private void civicHouse(Builder b, Profile p, int variant) {
        if (p == null) throw new IllegalStateException("Не задан профиль муниципального здания");
        int hx = p.halfX();
        int hz = p.halfZ();

        // I. Подземная лента и пол, с открытым подходом к фасаду.
        b.floor(-hx, hx, -hz, hz, 0, p.foundation(), BlockRole.RESIDENT, 1);
        b.floor(-1, 1, -hz - 3, -hz - 1, 0, p.foundation(), BlockRole.DECORATION, 1);

        // II. Стены, угловые стойки и окна. Проём 2x2 остаётся свободным.
        for (int y = 1; y <= 4; y++) {
            for (int x = -hx; x <= hx; x++) {
                if (!(x == 0 && y <= 2)) b.block(x, y, -hz, p.wall(), BlockRole.RESIDENT, 2);
                b.block(x, y, hz, p.wall(), BlockRole.RESIDENT, 2);
            }
            for (int z = -hz + 1; z < hz; z++) {
                b.block(-hx, y, z, p.wall(), BlockRole.RESIDENT, 2);
                b.block(hx, y, z, p.wall(), BlockRole.RESIDENT, 2);
            }
        }
        for (int x : new int[]{-hx, hx}) for (int z : new int[]{-hz, hz}) {
            for (int y = 1; y <= 4; y++) b.replace(x, y, z, p.beam(), BlockRole.RESIDENT, 2);
        }
        for (int x : new int[]{-Math.max(2, hx / 2), Math.max(2, hx / 2)}) {
            b.replace(x, 2, -hz, Material.GLASS_PANE, BlockRole.DECORATION, 2);
            b.replace(x, 2, hz, Material.GLASS_PANE, BlockRole.DECORATION, 2);
        }

        // III. Полная крыша и нормальная двухблочная дверь.
        b.floor(-hx - 1, hx + 1, -hz - 1, hz + 1, 5, p.roof(), BlockRole.RESIDENT, 3);
        b.door(0, 1, -hz, p.door(), BlockFace.NORTH, Door.Hinge.LEFT, 3);
        for (int x = -hx; x <= hx; x += Math.max(2, hx)) {
            b.block(x, 6, 0, Material.LIGHTNING_ROD, BlockRole.DECORATION, 3);
        }

        // IV. Оснащение не перекрывает центральный проход.
        int equipmentCount = 3 + variant % 5;
        for (int index = 0; index < equipmentCount; index++) {
            int x = -hx + 1 + (index % Math.max(1, hx - 1));
            int z = hz - 1 - (index / Math.max(1, hx - 1));
            b.block(x, 1, z, equipment(p.equipment()), BlockRole.RESIDENT, 4);
        }
        b.block(hx - 1, 1, -hz + 1, Material.BARREL, BlockRole.DECORATION, 4);
        b.block(-hx + 1, 1, -hz + 1, Material.LANTERN, BlockRole.DECORATION, 4);

        // V. Двор и узнаваемый вертикальный акцент; размеры каждой модели различаются.
        int courtyard = hz + 5 + variant % 3;
        for (int x = -hx - 2; x <= hx + 2; x++) {
            b.block(x, 0, courtyard, Material.STONE_BRICKS, BlockRole.RESIDENT, 5);
        }
        for (int y = 1; y <= 2 + variant % 4; y++) {
            b.block(hx + 2, y, courtyard, p.beam(), BlockRole.RESIDENT, 5);
        }
        b.block(hx + 2, 3 + variant % 4, courtyard, Material.LANTERN, BlockRole.DECORATION, 5);
        b.block(-hx - 2, 1, courtyard, Material.BELL, BlockRole.DECORATION, 5);
    }

    private Material equipment(Material requested) {
        // Ведро и книга являются предметами, а не размещаемыми блоками.
        if (requested == Material.WRITABLE_BOOK) return Material.LECTERN;
        if (requested == Material.WATER_BUCKET) return Material.WATER_CAULDRON;
        if (requested == Material.CROSSBOW) return Material.FLETCHING_TABLE;
        return requested;
    }

    private void fortressWall(Builder b) {
        b.floor(-9, 9, -1, 1, 0, Material.STONE_BRICKS, BlockRole.RESIDENT, 1);
        for (int x = -9; x <= 9; x++) for (int y = 1; y <= 3; y++)
            b.block(x, y, 0, Material.STONE_BRICKS, BlockRole.RESIDENT, 2);
        for (int x = -9; x <= 9; x++) {
            b.block(x, 4, 0, x % 2 == 0 ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE,
                    BlockRole.RESIDENT, 3);
        }
        for (int x : new int[]{-9, 9}) for (int z = -2; z <= 2; z++) for (int y = 1; y <= 5; y++)
            b.block(x, y, z, Material.DEEPSLATE_BRICKS, BlockRole.RESIDENT, 4);
        for (int x = -8; x <= 8; x++) b.block(x, 4, 1, Material.SMOOTH_STONE, BlockRole.RESIDENT, 5);
        b.block(-9, 6, 0, Material.LANTERN, BlockRole.DECORATION, 5);
        b.block(9, 6, 0, Material.LANTERN, BlockRole.DECORATION, 5);
    }

    private void cityMoat(Builder b) {
        b.floor(-9, 9, -2, 2, 0, Material.MUD_BRICKS, BlockRole.RESIDENT, 1);
        for (int x = -9; x <= 9; x++) {
            b.block(x, 1, -2, Material.STONE_BRICKS, BlockRole.RESIDENT, 2);
            b.block(x, 1, 2, Material.STONE_BRICKS, BlockRole.RESIDENT, 2);
        }
        for (int x = -9; x <= 9; x++) for (int z = -1; z <= 1; z++)
            b.block(x, 1, z, Material.WATER, BlockRole.DECORATION, 3);
        for (int z = -2; z <= 2; z++) b.block(0, 2, z, Material.DARK_OAK_PLANKS, BlockRole.RESIDENT, 3);
        for (int z : new int[]{-2, 2}) for (int y = 2; y <= 3; y++) {
            b.block(-9, y, z, Material.STONE_BRICK_WALL, BlockRole.RESIDENT, 4);
            b.block(9, y, z, Material.STONE_BRICK_WALL, BlockRole.RESIDENT, 4);
        }
        for (int x = -8; x <= 8; x += 2) {
            b.block(x, 2, -2, Material.MOSSY_STONE_BRICKS, BlockRole.RESIDENT, 5);
            b.block(x, 2, 2, Material.MOSSY_STONE_BRICKS, BlockRole.RESIDENT, 5);
        }
        b.block(0, 3, -2, Material.LANTERN, BlockRole.DECORATION, 5);
    }

    private void dam(Builder b) {
        b.floor(-9, 9, -2, 2, 0, Material.DEEPSLATE_BRICKS, BlockRole.RESIDENT, 1);
        for (int x = -9; x <= 9; x++) for (int y = 1; y <= 3; y++)
            b.block(x, y, 0, Material.STONE_BRICKS, BlockRole.RESIDENT, 2);
        for (int x = -9; x <= 9; x++) for (int y = 4; y <= 6; y++)
            b.block(x, y, 0, Material.POLISHED_ANDESITE, BlockRole.RESIDENT, 3);
        for (int x : new int[]{-6, 0, 6}) for (int z = -1; z <= 1; z++) for (int y = 1; y <= 6; y++)
            b.block(x, y, z, Material.IRON_BLOCK, BlockRole.RESIDENT, 4);
        for (int x = -9; x <= 9; x++) b.block(x, 7, 0, Material.SMOOTH_STONE, BlockRole.RESIDENT, 5);
        for (int x = -8; x <= 8; x += 4) b.block(x, 8, 0, Material.LANTERN, BlockRole.DECORATION, 5);
    }

    private record Profile(int halfX, int halfZ, Material foundation, Material wall,
                           Material beam, Material roof, Material door, Material equipment) { }

    private static final class Builder {
        private final int level;
        private final Map<BlockOffset, BlueprintBlock> blocks = new LinkedHashMap<>();

        private Builder(int level) { this.level = level; }

        private void block(int x, int y, int z, Material material, BlockRole role, int stage) {
            if (stage > level) return;
            blocks.putIfAbsent(new BlockOffset(x, y, z), new BlueprintBlock(material, role, stage));
        }

        private void replace(int x, int y, int z, Material material, BlockRole role, int stage) {
            if (stage > level) return;
            BlockOffset offset = new BlockOffset(x, y, z);
            BlueprintBlock previous = blocks.get(offset);
            if (previous == null || previous.stage() == stage) {
                blocks.put(offset, new BlueprintBlock(material, role, stage));
            }
        }

        private void floor(int minX, int maxX, int minZ, int maxZ, int y,
                           Material material, BlockRole role, int stage) {
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
                block(x, y, z, material, role, stage);
        }

        private void door(int x, int y, int z, Material material, BlockFace facing,
                          Door.Hinge hinge, int stage) {
            if (stage > level) return;
            blocks.put(new BlockOffset(x, y, z), new BlueprintBlock(material, BlockRole.DECORATION, stage,
                    facing, Axis.Y, Bisected.Half.BOTTOM, hinge));
            blocks.put(new BlockOffset(x, y + 1, z), new BlueprintBlock(material, BlockRole.DECORATION, stage,
                    facing, Axis.Y, Bisected.Half.TOP, hinge));
        }
    }
}
