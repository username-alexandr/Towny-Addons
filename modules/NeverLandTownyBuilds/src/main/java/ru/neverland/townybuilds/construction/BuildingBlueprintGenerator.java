package ru.neverland.townybuilds.construction;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Процедурные чертежи городских зданий и Чудес NeverLand.
 * Локальная отрицательная ось Z всегда направлена к главному входу.
 */
public final class BuildingBlueprintGenerator {
    private static final Set<String> CORE_BUILDINGS = Set.of(
            "town_hall", "forge", "barracks", "market", "miners_guild", "temple",
            "great_library", "agrarian_complex"
    );
    private static final ExpansionBlueprintGenerator EXPANSION_GENERATOR = new ExpansionBlueprintGenerator();
    private static final Set<String> EXPANSION_BUILDINGS = EXPANSION_GENERATOR.supportedProjects();
    private static final ImportedModelBlueprintGenerator IMPORTED_GENERATOR = new ImportedModelBlueprintGenerator();
    private static final Set<String> IMPORTED_BUILDINGS = IMPORTED_GENERATOR.supportedProjects();
    private static final Set<String> BUILDINGS = union(union(CORE_BUILDINGS, EXPANSION_BUILDINGS), IMPORTED_BUILDINGS);
    private static final Set<String> WONDERS = Set.of(
            "sun_pyramid", "great_colosseum", "alexandria_lighthouse", "hanging_gardens",
            "archmage_spire"
    );
    private static final Set<String> SUPPORTED = union(BUILDINGS, WONDERS);
    private static final String[] GENERIC_STAGES = {
            "Основание", "Расширение", "Городской корпус", "Вертикальный акцент", "Монументальное завершение"
    };

    public Set<String> supportedProjects() {
        return SUPPORTED;
    }

    public Set<String> supportedBuildings() {
        return BUILDINGS;
    }

    public Set<String> supportedWonders() {
        return WONDERS;
    }

    public int maximumStage(String rawProjectId) {
        String projectId = rawProjectId == null ? "" : rawProjectId.toLowerCase(Locale.ROOT);
        return WONDERS.contains(projectId) ? 1 : BUILDINGS.contains(projectId) ? 5 : 0;
    }

    public BlueprintPlan generate(String rawProjectId, int rawLevel) {
        String projectId = rawProjectId == null ? "" : rawProjectId.toLowerCase(Locale.ROOT);
        int maximumStage = maximumStage(projectId);
        int level = Math.max(1, Math.min(maximumStage, rawLevel));
        if (!SUPPORTED.contains(projectId)) return null;
        if (EXPANSION_BUILDINGS.contains(projectId)) {
            return EXPANSION_GENERATOR.generate(projectId, level);
        }
        if (IMPORTED_BUILDINGS.contains(projectId)) {
            return IMPORTED_GENERATOR.generate(projectId, level);
        }
        Builder builder = new Builder(level, ConstructionSite.CURRENT_ARCHITECTURE_VERSION);
        switch (projectId) {
            case "town_hall" -> vanillaTownHall(builder);
            case "forge" -> vanillaForge(builder);
            case "barracks" -> vanillaBarracks(builder);
            case "market" -> vanillaMarket(builder);
            case "miners_guild" -> vanillaMinersGuild(builder);
            case "temple" -> vanillaTemple(builder);
            case "great_library" -> vanillaLibrary(builder);
            case "agrarian_complex" -> vanillaAgrarian(builder);
            case "sun_pyramid" -> sunPyramid(builder);
            case "great_colosseum" -> greatColosseum(builder);
            case "alexandria_lighthouse" -> alexandriaLighthouse(builder);
            case "hanging_gardens" -> hangingGardens(builder);
            case "archmage_spire" -> archmageSpire(builder);
            default -> throw new IllegalStateException("Неизвестный чертёж " + projectId);
        }
        return new BlueprintPlan(projectId, level, stageName(projectId, level), builder.blocks);
    }

    /** План первой ванильной архитектуры 0.3.0–0.3.1 нужен для безопасного обновления площадок. */
    public BlueprintPlan generateArchitecture2(String rawProjectId, int rawLevel) {
        String projectId = rawProjectId == null ? "" : rawProjectId.toLowerCase(Locale.ROOT);
        int level = Math.max(1, Math.min(5, rawLevel));
        if (!CORE_BUILDINGS.contains(projectId)) return null;
        Builder builder = new Builder(level, 2);
        switch (projectId) {
            case "town_hall" -> vanillaTownHall(builder);
            case "forge" -> vanillaForge(builder);
            case "barracks" -> vanillaBarracks(builder);
            case "market" -> vanillaMarket(builder);
            case "miners_guild" -> vanillaMinersGuild(builder);
            case "temple" -> vanillaTemple(builder);
            case "great_library" -> vanillaLibrary(builder);
            case "agrarian_complex" -> vanillaAgrarian(builder);
            default -> throw new IllegalStateException("Неизвестный чертёж " + projectId);
        }
        return new BlueprintPlan(projectId, level, stageName(projectId, level), builder.blocks);
    }

    /** План архитектуры 0.3.2 нужен для безопасного обновления существующих площадок. */
    public BlueprintPlan generateArchitecture3(String rawProjectId, int rawLevel) {
        String projectId = rawProjectId == null ? "" : rawProjectId.toLowerCase(Locale.ROOT);
        int level = Math.max(1, Math.min(5, rawLevel));
        if (!CORE_BUILDINGS.contains(projectId)) return null;
        Builder builder = new Builder(level, 3);
        switch (projectId) {
            case "town_hall" -> vanillaTownHall(builder);
            case "forge" -> vanillaForge(builder);
            case "barracks" -> vanillaBarracks(builder);
            case "market" -> vanillaMarket(builder);
            case "miners_guild" -> vanillaMinersGuild(builder);
            case "temple" -> vanillaTemple(builder);
            case "great_library" -> vanillaLibrary(builder);
            case "agrarian_complex" -> vanillaAgrarian(builder);
            default -> throw new IllegalStateException("Неизвестный чертёж " + projectId);
        }
        return new BlueprintPlan(projectId, level, stageName(projectId, level), builder.blocks);
    }

    public BlueprintPlan generateForArchitecture(String projectId, int level, int architectureVersion) {
        String normalized = projectId == null ? "" : projectId.toLowerCase(Locale.ROOT);
        if (WONDERS.contains(normalized)) return generate(normalized, level);
        if (IMPORTED_BUILDINGS.contains(normalized)) return IMPORTED_GENERATOR.generate(normalized, level);
        // Процедурный источник версии 5 остаётся доступным после будущего перехода на .schem,
        // чтобы активные площадки можно было безопасно восстановить и перенести.
        if (EXPANSION_BUILDINGS.contains(normalized)) return EXPANSION_GENERATOR.generate(normalized, level);
        if (architectureVersion <= 1) return generateLegacy(projectId, level);
        if (architectureVersion == 2) return generateArchitecture2(projectId, level);
        if (architectureVersion == 3) return generateArchitecture3(projectId, level);
        return generate(projectId, level);
    }

    /** План архитектуры 0.2.x используется только для безопасного обновления существующих площадок. */
    public BlueprintPlan generateLegacy(String rawProjectId, int rawLevel) {
        String projectId = rawProjectId == null ? "" : rawProjectId.toLowerCase(Locale.ROOT);
        int level = Math.max(1, Math.min(5, rawLevel));
        if (!CORE_BUILDINGS.contains(projectId)) return null;
        Builder builder = new Builder(level, 1);
        switch (projectId) {
            case "town_hall" -> townHall(builder);
            case "forge" -> forge(builder);
            case "barracks" -> barracks(builder);
            case "market" -> market(builder);
            case "miners_guild" -> minersGuild(builder);
            case "temple" -> temple(builder);
            case "great_library" -> library(builder);
            case "agrarian_complex" -> agrarian(builder);
            default -> throw new IllegalStateException("Неизвестный старый чертёж " + projectId);
        }
        return new BlueprintPlan(projectId, level, stageName(projectId, level), builder.blocks);
    }

    public String stageName(String projectId, int level) {
        if (projectId != null && EXPANSION_BUILDINGS.contains(projectId.toLowerCase(Locale.ROOT))) {
            return EXPANSION_GENERATOR.stageName(projectId.toLowerCase(Locale.ROOT), level);
        }
        if (projectId != null && IMPORTED_BUILDINGS.contains(projectId.toLowerCase(Locale.ROOT))) {
            return IMPORTED_GENERATOR.stageName(projectId.toLowerCase(Locale.ROOT), level);
        }
        String[] stages = switch (projectId) {
            case "town_hall" -> new String[]{"Дом городского совета", "Дом писаря", "Архивная изба", "Деревенская звонница", "Площадь совета"};
            case "forge" -> new String[]{"Дом кузнеца", "Плавильная мастерская", "Склад руды", "Кузнечный двор", "Дом мастера"};
            case "barracks" -> new String[]{"Караульный дом", "Деревенская оружейная", "Учебный двор", "Сторожевая башня", "Гарнизонная усадьба"};
            case "market" -> new String[]{"Дом торговца", "Первые лавки", "Торговые ряды", "Товарный амбар", "Рыночная площадь"};
            case "miners_guild" -> new String[]{"Дом горняка", "Шахтная сторожка", "Сортировочный сарай", "Деревянный копёр", "Посёлок гильдии"};
            case "temple" -> new String[]{"Деревенская часовня", "Дом служителя", "Приют паломников", "Каменная колокольня", "Приходской двор"};
            case "great_library" -> new String[]{"Дом библиотекаря", "Читальная изба", "Архивный дом", "Башня картографа", "Учёный двор"};
            case "agrarian_complex" -> new String[]{"Фермерский дом", "Деревенский амбар", "Огородная теплица", "Зерновой склад", "Мельничная усадьба"};
            case "sun_pyramid" -> new String[]{"Пирамида Солнца"};
            case "great_colosseum" -> new String[]{"Великий Колизей"};
            case "alexandria_lighthouse" -> new String[]{"Александрийский маяк"};
            case "hanging_gardens" -> new String[]{"Висячие сады"};
            case "archmage_spire" -> new String[]{"Шпиль Архимагов"};
            default -> GENERIC_STAGES;
        };
        return stages[Math.max(1, Math.min(stages.length, level)) - 1];
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> combined = new LinkedHashSet<>(first);
        combined.addAll(second);
        return Set.copyOf(combined);
    }

    /**
     * Ступенчатая мезоамериканская пирамида с единственной широкой лестницей и солнечным храмом.
     * Все несущие ярусы ставят жители; золото, зелень, свет и двери относятся к отделке.
     */
    private void sunPyramid(Builder b) {
        if (!b.level(1)) return;

        for (int tier = 0; tier < 5; tier++) {
            int edge = 12 - tier * 2;
            int y = tier * 2;
            Material material = tier % 2 == 0 ? Material.SANDSTONE : Material.CUT_SANDSTONE;
            b.floor(-edge, edge, -edge, edge, y, material, 1);
            b.floor(-edge, edge, -edge, edge, y + 1, material, 1);
            b.accentRing(-edge, edge, -edge, edge, y + 1, Material.CHISELED_SANDSTONE,
                    BlockRole.DECORATION, 1);

            int gardenEdge = edge - 1;
            if (gardenEdge >= 3) {
                for (int x = -gardenEdge; x <= gardenEdge; x += 4) {
                    b.block(x, y + 2, -gardenEdge, Material.MOSS_BLOCK, BlockRole.DECORATION, 1);
                    b.block(x, y + 3, -gardenEdge, (x & 1) == 0
                            ? Material.FLOWERING_AZALEA : Material.AZALEA, BlockRole.DECORATION, 1);
                }
                for (int z = -gardenEdge + 2; z <= gardenEdge; z += 5) {
                    b.block(-gardenEdge, y + 2, z, Material.MOSS_BLOCK, BlockRole.DECORATION, 1);
                    b.block(gardenEdge, y + 2, z, Material.MOSS_BLOCK, BlockRole.DECORATION, 1);
                }
            }
        }

        // Широкая лестница поднимается с земли до верхней храмовой террасы.
        for (int step = 0; step <= 9; step++) {
            int z = -13 + step;
            for (int x = -2; x <= 2; x++) {
                b.replaceStair(x, step, z, Material.SANDSTONE_STAIRS, BlockFace.SOUTH,
                        Bisected.Half.BOTTOM, BlockRole.DECORATION, 1);
            }
            if (step % 2 == 0) {
                b.block(-3, step, z, Material.GOLD_BLOCK, BlockRole.DECORATION, 1);
                b.block(3, step, z, Material.GOLD_BLOCK, BlockRole.DECORATION, 1);
            }
        }

        // Верхний храм доступен через двойную дверь и не перекрывает лестницу.
        b.floor(-4, 4, -3, 4, 10, Material.SMOOTH_SANDSTONE, 1);
        for (int x : new int[]{-4, 4}) for (int z : new int[]{-3, 4}) {
            b.pillar(x, z, 11, 14, Material.CUT_SANDSTONE, 1);
        }
        for (int y = 11; y <= 13; y++) {
            for (int x = -4; x <= 4; x++) {
                if (!(y <= 12 && (x == -1 || x == 0))) {
                    b.block(x, y, -3, Material.SANDSTONE, BlockRole.RESIDENT, 1);
                }
                b.block(x, y, 4, Material.SANDSTONE, BlockRole.RESIDENT, 1);
            }
            for (int z = -2; z <= 3; z++) {
                b.block(-4, y, z, Material.SANDSTONE, BlockRole.RESIDENT, 1);
                b.block(4, y, z, Material.SANDSTONE, BlockRole.RESIDENT, 1);
            }
        }
        b.door(-1, 11, -3, Material.BIRCH_DOOR, BlockFace.NORTH, Door.Hinge.LEFT, 1);
        b.door(0, 11, -3, Material.BIRCH_DOOR, BlockFace.NORTH, Door.Hinge.RIGHT, 1);
        b.accent(-4, 12, 0, Material.YELLOW_STAINED_GLASS_PANE, BlockRole.DECORATION, 1);
        b.accent(4, 12, 0, Material.YELLOW_STAINED_GLASS_PANE, BlockRole.DECORATION, 1);
        b.flatRoof(-5, 5, -4, 5, 14, Material.SMOOTH_SANDSTONE, 1);
        b.pyramidRoof(-4, 4, -3, 4, 15, Material.GOLD_BLOCK, 1);
        b.accent(0, 18, 0, Material.BEACON, BlockRole.DECORATION, 1);
        for (int x : new int[]{-5, 5}) for (int z : new int[]{-4, 5}) {
            b.block(x, 15, z, Material.LANTERN, BlockRole.DECORATION, 1);
        }
    }

    /** Овальная арена с двумя поясами арок, трибунами и сквозными северными/южными воротами. */
    private void greatColosseum(Builder b) {
        if (!b.level(1)) return;

        b.ellipseDisc(0, 0, 14, 10, 0, Material.STONE_BRICKS, BlockRole.RESIDENT, 1);
        b.ellipseDisc(0, 0, 7, 3, 0, Material.SMOOTH_STONE, BlockRole.DECORATION, 1);

        // Каменные трибуны спускаются к арене.
        int[][] seats = {{12, 8, 1}, {11, 7, 2}, {10, 6, 3}, {9, 5, 4}};
        for (int[] seat : seats) {
            b.ellipseRing(0, 0, seat[0], seat[1], seat[2], Material.QUARTZ_BLOCK,
                    BlockRole.RESIDENT, 1);
            b.ellipseSeatRing(0, 0, seat[0] - 1, seat[1] - 1, seat[2], Material.QUARTZ_STAIRS, 1);
        }

        // Два открытых этажа аркад: колонны несущие, пояса и акценты устанавливает плагин.
        b.ellipsePillars(0, 0, 14, 10, 28, 1, 4, Material.QUARTZ_PILLAR, 1);
        b.ellipseRing(0, 0, 14, 10, 4, Material.QUARTZ_BRICKS, BlockRole.DECORATION, 1);
        b.ellipseRing(0, 0, 13, 9, 5, Material.STONE_BRICKS, BlockRole.RESIDENT, 1);
        b.ellipsePillars(0, 0, 14, 10, 28, 6, 9, Material.QUARTZ_PILLAR, 1);
        b.ellipseRing(0, 0, 14, 10, 9, Material.QUARTZ_BRICKS, BlockRole.DECORATION, 1);
        b.ellipseRing(0, 0, 13, 9, 10, Material.CUT_SANDSTONE, BlockRole.RESIDENT, 1);
        b.ellipseRing(0, 0, 14, 10, 11, Material.QUARTZ_BLOCK, BlockRole.DECORATION, 1);

        // Сквозные ворота размером 3×4, без ложных дверей в стене.
        for (int z : new int[]{-10, 10}) {
            for (int x = -1; x <= 1; x++) for (int y = 1; y <= 8; y++) b.clear(x, y, z, 1);
            for (int x = -2; x <= 2; x++) {
                b.block(x, 9, z, Material.QUARTZ_BRICKS, BlockRole.DECORATION, 1);
            }
            b.pillar(-2, z, 1, 8, Material.QUARTZ_PILLAR, 1);
            b.pillar(2, z, 1, 8, Material.QUARTZ_PILLAR, 1);
        }
        for (int x = -1; x <= 1; x++) {
            b.path(x, x, -12, -9, Material.SMOOTH_STONE, 1);
            b.path(x, x, 9, 12, Material.SMOOTH_STONE, 1);
        }
        for (int angle = 0; angle < 16; angle++) {
            double radians = angle * Math.PI * 2.0 / 16.0;
            int x = (int) Math.round(Math.cos(radians) * 14);
            int z = (int) Math.round(Math.sin(radians) * 10);
            b.block(x, 12, z, angle % 2 == 0 ? Material.RED_WOOL : Material.GOLD_BLOCK,
                    BlockRole.DECORATION, 1);
        }
    }

    /** Трёхъярусный маяк: укреплённый портовый цоколь, две башни и стеклянный фонарь. */
    private void alexandriaLighthouse(Builder b) {
        if (!b.level(1)) return;

        b.floor(-8, 8, -7, 7, 0, Material.PRISMARINE_BRICKS, 1);
        b.room(-8, 8, -7, 7, 1, 5, Material.STONE_BRICKS, Material.QUARTZ_PILLAR, 1, 2);
        for (int x = -1; x <= 0; x++) {
            b.replaceStair(x, 0, -8, Material.STONE_BRICK_STAIRS, BlockFace.SOUTH,
                    Bisected.Half.BOTTOM, BlockRole.DECORATION, 1);
        }
        b.flatRoof(-9, 9, -8, 8, 6, Material.SMOOTH_STONE, 1);
        b.ring(-9, 9, -8, 8, 7, Material.STONE_BRICK_WALL, BlockRole.DECORATION, 1);
        for (int x : new int[]{-7, 7}) {
            b.accent(x, 3, -7, Material.BLUE_STAINED_GLASS, BlockRole.DECORATION, 1);
            b.accent(x, 3, 7, Material.BLUE_STAINED_GLASS, BlockRole.DECORATION, 1);
        }

        b.taperedSquareTower(-5, 5, -4, 4, 7, 14, Material.QUARTZ_BLOCK,
                Material.QUARTZ_PILLAR, 1);
        b.flatRoof(-6, 6, -5, 5, 15, Material.SMOOTH_QUARTZ, 1);
        b.ring(-6, 6, -5, 5, 16, Material.IRON_BARS, BlockRole.DECORATION, 1);
        b.taperedSquareTower(-4, 4, -3, 3, 16, 23, Material.SMOOTH_QUARTZ,
                Material.QUARTZ_PILLAR, 1);
        b.flatRoof(-5, 5, -4, 4, 24, Material.QUARTZ_BRICKS, 1);
        b.ring(-5, 5, -4, 4, 25, Material.IRON_BARS, BlockRole.DECORATION, 1);
        b.taperedSquareTower(-3, 3, -2, 2, 25, 30, Material.QUARTZ_BRICKS,
                Material.QUARTZ_PILLAR, 1);
        b.spiralStairs(1, 30, Material.QUARTZ_STAIRS, 1);

        // Вертикальные окна каждого яруса сохраняют узнаваемый силуэт маяка.
        for (int y : new int[]{10, 13, 19, 22, 27, 29}) {
            int z = y < 16 ? -4 : y < 25 ? -3 : -2;
            b.accent(0, y, z, Material.BLUE_STAINED_GLASS, BlockRole.DECORATION, 1);
            b.accent(0, y, -z, Material.BLUE_STAINED_GLASS, BlockRole.DECORATION, 1);
        }

        b.flatRoof(-4, 4, -3, 3, 31, Material.SMOOTH_QUARTZ, 1);
        for (int y = 32; y <= 34; y++) b.ring(-2, 2, -2, 2, y, Material.GLASS,
                BlockRole.DECORATION, 1);
        b.block(0, 32, 0, Material.BEACON, BlockRole.DECORATION, 1);
        b.flatRoof(-3, 3, -3, 3, 35, Material.GOLD_BLOCK, 1);
        b.pyramidRoof(-2, 2, -2, 2, 36, Material.GOLD_BLOCK, 1);
        b.block(0, 39, 0, Material.CAMPFIRE, BlockRole.DECORATION, 1);
        for (int x : new int[]{-4, 4}) for (int z : new int[]{-3, 3}) {
            b.block(x, 32, z, Material.SEA_LANTERN, BlockRole.DECORATION, 1);
        }
    }

    /** Три доступные террасы с колоннадами, лестницами, безопасными водными лентами и садами. */
    private void hangingGardens(Builder b) {
        if (!b.level(1)) return;

        b.floor(-12, 12, -10, 10, 0, Material.STONE_BRICKS, 1);
        b.gardenColonnade(-12, 12, -10, 10, 1, 4, Material.STONE_BRICKS, Material.QUARTZ_PILLAR, 1);
        b.flatRoof(-11, 11, -9, 9, 5, Material.MOSS_BLOCK, 1);

        b.gardenColonnade(-9, 9, -7, 7, 6, 9, Material.MOSSY_STONE_BRICKS, Material.QUARTZ_PILLAR, 1);
        b.flatRoof(-8, 8, -6, 6, 10, Material.MOSS_BLOCK, 1);

        b.gardenColonnade(-6, 6, -4, 4, 11, 14, Material.STONE_BRICKS, Material.QUARTZ_PILLAR, 1);
        b.flatRoof(-5, 5, -3, 3, 15, Material.MOSS_BLOCK, 1);

        // Три отдельные лестницы гарантируют путь с земли до самой верхней площадки.
        b.terraceStairs(-2, 2, -12, -8, 1, Material.STONE_BRICK_STAIRS, 1);
        b.terraceStairs(-2, 2, -9, -5, 6, Material.STONE_BRICK_STAIRS, 1);
        b.terraceStairs(-2, 2, -6, -2, 11, Material.QUARTZ_STAIRS, 1);

        // Голубое стекло повторяет водопады концепта, но не разливается за пределы постройки.
        for (int x : new int[]{-8, 0, 8}) {
            for (int y = 1; y <= 5; y++) b.block(x, y, -10, Material.LIGHT_BLUE_STAINED_GLASS,
                    BlockRole.DECORATION, 1);
        }
        for (int x : new int[]{-5, 5}) {
            for (int y = 6; y <= 10; y++) b.block(x, y, -7, Material.LIGHT_BLUE_STAINED_GLASS,
                    BlockRole.DECORATION, 1);
        }
        for (int y = 11; y <= 15; y++) b.block(0, y, -4, Material.LIGHT_BLUE_STAINED_GLASS,
                BlockRole.DECORATION, 1);

        b.gardenEdge(-11, 11, -9, 9, 6, 3, 1);
        b.gardenEdge(-8, 8, -6, 6, 11, 2, 1);
        b.gardenEdge(-5, 5, -3, 3, 16, 2, 1);
        b.room(-3, 3, -1, 3, 16, 18, Material.QUARTZ_BRICKS, Material.QUARTZ_PILLAR, 1, 1);
        b.flatRoof(-4, 4, -2, 4, 19, Material.MOSS_BLOCK, 1);
        b.block(0, 20, 1, Material.FLOWERING_AZALEA, BlockRole.DECORATION, 1);
    }

    /** Тёмная магическая башня с четырьмя сужающимися ярусами и аметистовой короной. */
    private void archmageSpire(Builder b) {
        if (!b.level(1)) return;

        b.cylinder(0, 0, 8, 0, Material.POLISHED_BLACKSTONE_BRICKS, BlockRole.RESIDENT, 1);
        for (int y = 1; y <= 7; y++) b.ringCircle(0, 0, 7, y, Material.DEEPSLATE_BRICKS,
                BlockRole.RESIDENT, 1);
        b.clear(0, 1, -7, 1);
        b.clear(0, 2, -7, 1);
        b.door(0, 1, -7, Material.DARK_OAK_DOOR, BlockFace.NORTH, Door.Hinge.LEFT, 1);
        b.replaceStair(0, 0, -8, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.SOUTH,
                Bisected.Half.BOTTOM, BlockRole.DECORATION, 1);
        b.magicWindows(7, 3, Material.PURPLE_STAINED_GLASS, 1);
        b.cylinder(0, 0, 8, 8, Material.POLISHED_BLACKSTONE_BRICKS, BlockRole.DECORATION, 1);
        b.ringCircle(0, 0, 8, 9, Material.IRON_BARS, BlockRole.DECORATION, 1);

        for (int y = 9; y <= 16; y++) b.ringCircle(0, 0, 5, y, Material.PURPUR_BLOCK,
                BlockRole.RESIDENT, 1);
        b.magicWindows(5, 12, Material.PURPLE_STAINED_GLASS, 1);
        b.cylinder(0, 0, 6, 17, Material.CRYING_OBSIDIAN, BlockRole.DECORATION, 1);
        b.ringCircle(0, 0, 6, 18, Material.IRON_BARS, BlockRole.DECORATION, 1);

        for (int y = 18; y <= 25; y++) b.ringCircle(0, 0, 4, y, Material.POLISHED_BLACKSTONE_BRICKS,
                BlockRole.RESIDENT, 1);
        b.magicWindows(4, 21, Material.PURPLE_STAINED_GLASS, 1);
        b.cylinder(0, 0, 5, 26, Material.PURPUR_BLOCK, BlockRole.DECORATION, 1);
        b.ringCircle(0, 0, 5, 27, Material.IRON_BARS, BlockRole.DECORATION, 1);

        for (int y = 27; y <= 32; y++) b.ringCircle(0, 0, 3, y, Material.CRYING_OBSIDIAN,
                BlockRole.RESIDENT, 1);
        b.spiralStairs(1, 32, Material.PURPUR_STAIRS, 1);
        b.magicWindows(3, 29, Material.AMETHYST_BLOCK, 1);
        b.cylinder(0, 0, 4, 33, Material.POLISHED_BLACKSTONE_BRICKS, BlockRole.DECORATION, 1);

        // Кристаллические рёбра стоят на каждой террасе и не висят в воздухе.
        b.crystalCrown(8, 9, 4, 1);
        b.crystalCrown(6, 18, 4, 1);
        b.crystalCrown(5, 27, 3, 1);
        b.crystalCrown(3, 34, 6, 1);
        b.block(0, 34, 0, Material.BEACON, BlockRole.DECORATION, 1);
        for (int y = 35; y <= 39; y++) {
            b.block(0, y, 0, Material.AMETHYST_BLOCK, BlockRole.DECORATION, 1);
        }
        b.block(0, 40, 0, Material.END_ROD, BlockRole.DECORATION, 1);
        for (int y : new int[]{9, 18, 27, 34}) {
            int radius = y == 9 ? 8 : y == 18 ? 6 : y == 27 ? 5 : 3;
            for (int[] point : new int[][]{{radius, 0}, {-radius, 0}, {0, radius}, {0, -radius}}) {
                b.block(point[0], y + 1, point[1], Material.SOUL_LANTERN, BlockRole.DECORATION, 1);
            }
        }
    }

    private void townHall(Builder b) {
        if (b.level(1)) {
            b.floor(-4, 4, -3, 3, 0, Material.POLISHED_ANDESITE, 1);
            b.room(-4, 4, -3, 3, 1, 4, Material.STONE_BRICKS, Material.STRIPPED_OAK_LOG, 1, 2);
            b.hipRoof(-5, 5, -4, 4, 5, Material.DARK_OAK_PLANKS, 1);
            b.block(0, 1, 0, Material.BELL, BlockRole.DECORATION, 1);
        }
        if (b.level(2)) {
            b.floor(-4, 4, -7, -4, 0, Material.POLISHED_ANDESITE, 2);
            for (int x : new int[]{-4, -2, 2, 4}) b.pillar(x, -6, 1, 4, Material.QUARTZ_PILLAR, 2);
            b.lineX(-5, 5, 5, -6, Material.SMOOTH_QUARTZ, BlockRole.DECORATION, 2);
            b.lineX(-5, 5, 0, -7, Material.STONE_BRICKS, BlockRole.RESIDENT, 2);
            b.block(0, 4, -6, Material.GOLD_BLOCK, BlockRole.DECORATION, 2);
        }
        if (b.level(3)) {
            b.floor(-9, -5, -2, 3, 0, Material.POLISHED_ANDESITE, 3);
            b.floor(5, 9, -2, 3, 0, Material.POLISHED_ANDESITE, 3);
            b.room(-9, -5, -2, 3, 1, 3, Material.STONE_BRICKS, Material.STRIPPED_OAK_LOG, 3, 0);
            b.room(5, 9, -2, 3, 1, 3, Material.STONE_BRICKS, Material.STRIPPED_OAK_LOG, 3, 0);
            b.flatRoof(-10, -5, -3, 4, 4, Material.DARK_OAK_PLANKS, 3);
            b.flatRoof(5, 10, -3, 4, 4, Material.DARK_OAK_PLANKS, 3);
        }
        if (b.level(4)) {
            b.floor(-2, 2, 4, 7, 5, Material.STONE_BRICKS, 4);
            b.room(-2, 2, 4, 7, 6, 10, Material.STONE_BRICKS, Material.QUARTZ_PILLAR, 4, 0);
            for (int x : new int[]{-1, 1}) b.block(x, 9, 4, Material.GOLD_BLOCK, BlockRole.DECORATION, 4);
            b.pyramidRoof(-3, 3, 3, 8, 11, Material.DARK_OAK_PLANKS, 4);
        }
        if (b.level(5)) {
            b.ring(-2, 2, 4, 7, 12, Material.SMOOTH_QUARTZ, BlockRole.DECORATION, 5);
            b.ring(-1, 1, 5, 6, 13, Material.AMETHYST_BLOCK, BlockRole.DECORATION, 5);
            b.block(0, 14, 5, Material.BEACON, BlockRole.DECORATION, 5);
            b.lineZ(-2, 7, 11, 0, Material.STONE_BRICKS, BlockRole.RESIDENT, 5);
            b.lineZ(-2, 7, 11, -10, Material.STONE_BRICKS, BlockRole.RESIDENT, 5);
            b.lineX(-10, 0, 0, 11, Material.STONE_BRICKS, BlockRole.RESIDENT, 5);
            b.lineX(0, 10, 0, 11, Material.STONE_BRICKS, BlockRole.RESIDENT, 5);
        }
    }

    private void forge(Builder b) {
        if (b.level(1)) {
            b.floor(-4, 4, -3, 3, 0, Material.POLISHED_BLACKSTONE_BRICKS, 1);
            b.room(-4, 4, -3, 3, 1, 3, Material.DEEPSLATE_BRICKS, Material.IRON_BLOCK, 1, 2);
            b.gableRoofX(-5, 5, -4, 4, 4, Material.POLISHED_BLACKSTONE, 1);
            b.block(-2, 1, 1, Material.ANVIL, BlockRole.DECORATION, 1);
            b.block(2, 1, 1, Material.SMITHING_TABLE, BlockRole.DECORATION, 1);
        }
        if (b.level(2)) {
            b.floor(5, 9, -2, 3, 0, Material.POLISHED_BLACKSTONE_BRICKS, 2);
            b.room(5, 9, -2, 3, 1, 4, Material.BRICKS, Material.IRON_BLOCK, 2, 0);
            b.flatRoof(5, 10, -3, 4, 5, Material.POLISHED_BLACKSTONE, 2);
            b.block(7, 1, 1, Material.BLAST_FURNACE, BlockRole.DECORATION, 2);
            b.block(8, 1, 1, Material.MAGMA_BLOCK, BlockRole.DECORATION, 2);
        }
        if (b.level(3)) {
            b.floor(-10, -5, -4, 4, 0, Material.STONE_BRICKS, 3);
            b.pillar(-10, -4, 1, 3, Material.IRON_BLOCK, 3);
            b.pillar(-10, 4, 1, 3, Material.IRON_BLOCK, 3);
            b.pillar(-5, -4, 1, 3, Material.IRON_BLOCK, 3);
            b.pillar(-5, 4, 1, 3, Material.IRON_BLOCK, 3);
            b.flatRoof(-10, -5, -4, 4, 4, Material.CUT_COPPER, 3);
            for (int z : new int[]{-2, 0, 2}) b.block(-8, 1, z, Material.ANVIL, BlockRole.DECORATION, 3);
        }
        if (b.level(4)) {
            b.chimney(-3, 2, 4, 11, Material.BRICKS, 4);
            b.chimney(7, 2, 5, 13, Material.BRICKS, 4);
            b.block(-3, 11, 2, Material.BRICKS, BlockRole.DECORATION, 4);
            b.block(7, 13, 2, Material.BRICKS, BlockRole.DECORATION, 4);
            b.block(-3, 12, 2, Material.CAMPFIRE, BlockRole.DECORATION, 4);
            b.block(7, 14, 2, Material.CAMPFIRE, BlockRole.DECORATION, 4);
        }
        if (b.level(5)) {
            b.lineX(-10, 9, 5, 5, Material.IRON_BLOCK, BlockRole.RESIDENT, 5);
            b.lineX(-10, 9, 7, 5, Material.IRON_BLOCK, BlockRole.RESIDENT, 5);
            for (int x : new int[]{-10, -5, 0, 5, 9}) b.pillar(x, 5, 1, 6, Material.IRON_BLOCK, 5);
            b.lineZ(-4, 5, 9, -10, Material.IRON_CHAIN, BlockRole.DECORATION, 5);
            b.block(-10, 0, 5, Material.CAULDRON, BlockRole.DECORATION, 5);
        }
    }

    private void barracks(Builder b) {
        if (b.level(1)) {
            b.floor(-6, 6, -3, 3, 0, Material.STONE_BRICKS, 1);
            b.room(-6, 6, -3, 3, 1, 3, Material.STONE_BRICKS, Material.STRIPPED_SPRUCE_LOG, 1, 2);
            b.gableRoofX(-7, 7, -4, 4, 4, Material.DEEPSLATE_TILES, 1);
            b.block(-3, 1, 1, Material.TARGET, BlockRole.DECORATION, 1);
        }
        if (b.level(2)) {
            b.floor(-4, 4, 4, 8, 0, Material.STONE_BRICKS, 2);
            b.room(-4, 4, 4, 8, 1, 3, Material.DEEPSLATE_BRICKS, Material.IRON_BLOCK, 2, 0);
            b.flatRoof(-5, 5, 4, 9, 4, Material.DEEPSLATE_TILES, 2);
            b.block(0, 1, 6, Material.SMITHING_TABLE, BlockRole.DECORATION, 2);
        }
        if (b.level(3)) {
            b.floor(-8, 8, -11, -4, 0, Material.POLISHED_ANDESITE, 3);
            b.lineX(-8, 8, 1, -11, Material.STONE_BRICKS, BlockRole.RESIDENT, 3);
            b.lineZ(-10, -4, 1, -8, Material.STONE_BRICKS, BlockRole.RESIDENT, 3);
            b.lineZ(-10, -4, 1, 8, Material.STONE_BRICKS, BlockRole.RESIDENT, 3);
            for (int x = -6; x <= 6; x += 3) b.block(x, 1, -7, Material.OAK_FENCE, BlockRole.DECORATION, 3);
        }
        if (b.level(4)) {
            for (int x : new int[]{-8, 8}) for (int z : new int[]{-10, 8}) {
                b.tower(x, z, 2, 6, Material.DEEPSLATE_BRICKS, Material.IRON_BLOCK, 4);
            }
            b.lineX(-8, -2, 5, -10, Material.DEEPSLATE_BRICKS, BlockRole.RESIDENT, 4);
            b.lineX(2, 8, 5, -10, Material.DEEPSLATE_BRICKS, BlockRole.RESIDENT, 4);
            b.lineX(-2, 2, 7, -10, Material.IRON_BLOCK, BlockRole.DECORATION, 4);
        }
        if (b.level(5)) {
            b.floor(-3, 3, 9, 12, 0, Material.DEEPSLATE_BRICKS, 5);
            b.room(-3, 3, 9, 12, 1, 7, Material.DEEPSLATE_BRICKS, Material.IRON_BLOCK, 5, 0);
            b.pyramidRoof(-4, 4, 8, 13, 8, Material.DEEPSLATE_TILES, 5);
            b.block(0, 8, 10, Material.RED_WOOL, BlockRole.DECORATION, 5);
        }
    }

    private void market(Builder b) {
        if (b.level(1)) {
            b.floor(-4, 4, -4, 4, 0, Material.SMOOTH_STONE, 1);
            for (int x : new int[]{-3, 3}) for (int z : new int[]{-3, 3}) b.pillar(x, z, 1, 3, Material.STRIPPED_OAK_LOG, 1);
            b.pyramidRoof(-4, 4, -4, 4, 4, Material.RED_WOOL, 1);
            b.block(0, 1, 0, Material.BELL, BlockRole.DECORATION, 1);
        }
        if (b.level(2)) {
            for (int x : new int[]{-8, 8}) for (int z = -6; z <= 6; z += 4) b.stall(x, z, false, 2);
            for (int z : new int[]{-8, 8}) for (int x = -4; x <= 4; x += 4) b.stall(x, z, true, 2);
        }
        if (b.level(3)) {
            b.ring(-10, 10, -10, 10, 0, Material.BRICKS, BlockRole.RESIDENT, 3);
            for (int x : new int[]{-10, 10}) for (int z = -10; z <= 10; z += 5) b.pillar(x, z, 1, 4, Material.STRIPPED_DARK_OAK_LOG, 3);
            for (int z : new int[]{-10, 10}) for (int x = -10; x <= 10; x += 5) b.pillar(x, z, 1, 4, Material.STRIPPED_DARK_OAK_LOG, 3);
            b.ring(-10, 10, -10, 10, 5, Material.YELLOW_WOOL, BlockRole.DECORATION, 3);
        }
        if (b.level(4)) {
            b.floor(-5, 5, 11, 16, 0, Material.BRICKS, 4);
            b.room(-5, 5, 11, 16, 1, 4, Material.BRICKS, Material.STRIPPED_DARK_OAK_LOG, 4, 0);
            b.gableRoofX(-6, 6, 10, 17, 5, Material.DARK_OAK_PLANKS, 4);
            for (int x = -3; x <= 3; x += 3) b.block(x, 1, 14, Material.BARREL, BlockRole.DECORATION, 4);
        }
        if (b.level(5)) {
            b.tower(0, 0, 3, 9, Material.CUT_COPPER, Material.GOLD_BLOCK, 5);
            b.pyramidRoof(-4, 4, -4, 4, 10, Material.WAXED_CUT_COPPER, 5);
            b.block(0, 12, 0, Material.EMERALD_BLOCK, BlockRole.DECORATION, 5);
            for (int x : new int[]{-7, 7}) for (int z : new int[]{-7, 7}) b.block(x, 5, z, Material.SEA_LANTERN, BlockRole.DECORATION, 5);
        }
    }

    private void minersGuild(Builder b) {
        if (b.level(1)) {
            b.floor(-4, 4, -3, 3, 0, Material.TUFF_BRICKS, 1);
            b.room(-4, 4, -3, 3, 1, 4, Material.TUFF_BRICKS, Material.STRIPPED_DARK_OAK_LOG, 1, 2);
            b.gableRoofX(-5, 5, -4, 4, 5, Material.DEEPSLATE_TILES, 1);
            b.block(0, 1, 1, Material.CARTOGRAPHY_TABLE, BlockRole.DECORATION, 1);
        }
        if (b.level(2)) {
            b.floor(-3, 3, 4, 10, 0, Material.DEEPSLATE_BRICKS, 2);
            for (int x = -4; x <= 4; x++) for (int y = 1; y <= 6 - Math.abs(x); y++) {
                b.block(x, y, 10, Material.DEEPSLATE_BRICKS, BlockRole.RESIDENT, 2);
            }
            for (int x : new int[]{-2, 2}) b.pillar(x, 7, 1, 4, Material.STRIPPED_DARK_OAK_LOG, 2);
            b.lineX(-2, 2, 5, 7, Material.STRIPPED_DARK_OAK_LOG, BlockRole.RESIDENT, 2);
            b.block(0, 1, 9, Material.RAIL, BlockRole.DECORATION, 2);
        }
        if (b.level(3)) {
            b.floor(-10, -5, -5, 5, 0, Material.COBBLED_DEEPSLATE, 3);
            for (int z = -4; z <= 4; z += 4) {
                b.box(-9, 1, z, 2, 2, 2, Material.OAK_PLANKS, BlockRole.RESIDENT, 3);
                b.block(-8, 3, z, z < 0 ? Material.REDSTONE_BLOCK : Material.RAW_IRON_BLOCK, BlockRole.DECORATION, 3);
            }
            b.lineZ(-5, 5, 4, -6, Material.IRON_CHAIN, BlockRole.DECORATION, 3);
        }
        if (b.level(4)) {
            for (int x : new int[]{-3, 3}) b.pillar(x, 8, 1, 11, Material.DARK_OAK_LOG, 4);
            b.lineX(-4, 4, 12, 8, Material.DARK_OAK_LOG, BlockRole.RESIDENT, 4);
            b.lineX(-3, 3, 9, 8, Material.IRON_CHAIN, BlockRole.DECORATION, 4);
            b.block(0, 8, 8, Material.IRON_BLOCK, BlockRole.DECORATION, 4);
        }
        if (b.level(5)) {
            b.floor(5, 10, -4, 5, 0, Material.TUFF_BRICKS, 5);
            b.room(5, 10, -4, 5, 1, 4, Material.TUFF_BRICKS, Material.IRON_BLOCK, 5, 0);
            b.flatRoof(5, 11, -5, 6, 5, Material.DEEPSLATE_TILES, 5);
            b.lineX(-10, 10, 6, 6, Material.IRON_BLOCK, BlockRole.RESIDENT, 5);
            b.block(10, 1, 3, Material.BLAST_FURNACE, BlockRole.DECORATION, 5);
        }
    }

    private void temple(Builder b) {
        if (b.level(1)) {
            b.floor(-3, 3, -6, 6, 0, Material.SMOOTH_QUARTZ, 1);
            b.room(-3, 3, -6, 6, 1, 6, Material.QUARTZ_BRICKS, Material.QUARTZ_PILLAR, 1, 2);
            b.gableRoofZ(-4, 4, -7, 7, 7, Material.PURPUR_BLOCK, 1);
            b.block(0, 1, 4, Material.GOLD_BLOCK, BlockRole.DECORATION, 1);
        }
        if (b.level(2)) {
            b.floor(-8, 8, -2, 2, 0, Material.SMOOTH_QUARTZ, 2);
            b.room(-8, -4, -2, 2, 1, 5, Material.QUARTZ_BRICKS, Material.QUARTZ_PILLAR, 2, 0);
            b.room(4, 8, -2, 2, 1, 5, Material.QUARTZ_BRICKS, Material.QUARTZ_PILLAR, 2, 0);
            b.gableRoofX(-9, -4, -3, 3, 6, Material.PURPUR_BLOCK, 2);
            b.gableRoofX(4, 9, -3, 3, 6, Material.PURPUR_BLOCK, 2);
        }
        if (b.level(3)) {
            b.floor(-4, 4, 7, 10, 0, Material.SMOOTH_QUARTZ, 3);
            b.room(-4, 4, 7, 10, 1, 5, Material.QUARTZ_BRICKS, Material.QUARTZ_PILLAR, 3, 0);
            b.pyramidRoof(-5, 5, 6, 11, 6, Material.PURPUR_BLOCK, 3);
            b.block(0, 1, 9, Material.BEACON, BlockRole.DECORATION, 3);
        }
        if (b.level(4)) {
            for (int x : new int[]{-6, 6}) {
                b.tower(x, -5, 2, 10, Material.QUARTZ_BRICKS, Material.QUARTZ_PILLAR, 4);
                b.pyramidRoof(x - 3, x + 3, -8, -2, 11, Material.GOLD_BLOCK, 4);
                b.block(x, 14, -5, Material.BELL, BlockRole.DECORATION, 4);
            }
        }
        if (b.level(5)) {
            b.ring(-4, 4, -4, 4, 8, Material.SMOOTH_QUARTZ, BlockRole.RESIDENT, 5);
            b.ring(-3, 3, -3, 3, 9, Material.AMETHYST_BLOCK, BlockRole.DECORATION, 5);
            b.ring(-2, 2, -2, 2, 10, Material.AMETHYST_BLOCK, BlockRole.DECORATION, 5);
            b.ring(-1, 1, -1, 1, 11, Material.GOLD_BLOCK, BlockRole.DECORATION, 5);
            b.block(0, 12, 0, Material.BEACON, BlockRole.DECORATION, 5);
        }
    }

    private void library(Builder b) {
        if (b.level(1)) {
            b.floor(-4, 4, -5, 5, 0, Material.OAK_PLANKS, 1);
            b.room(-4, 4, -5, 5, 1, 5, Material.BRICKS, Material.STRIPPED_DARK_OAK_LOG, 1, 2);
            b.gableRoofZ(-5, 5, -6, 6, 6, Material.DARK_OAK_PLANKS, 1);
            for (int z = -2; z <= 3; z += 2) for (int x : new int[]{-3, 3}) b.block(x, 1, z, Material.BOOKSHELF, BlockRole.DECORATION, 1);
        }
        if (b.level(2)) {
            for (int side : new int[]{-1, 1}) {
                int x1 = side < 0 ? -10 : 5;
                int x2 = side < 0 ? -5 : 10;
                b.floor(x1, x2, -4, 4, 0, Material.OAK_PLANKS, 2);
                b.room(x1, x2, -4, 4, 1, 4, Material.BRICKS, Material.STRIPPED_DARK_OAK_LOG, 2, 0);
                b.flatRoof(x1, x2, -5, 5, 5, Material.DARK_OAK_PLANKS, 2);
                int shelfX = side < 0 ? x1 + 1 : x2 - 1;
                for (int z = -2; z <= 2; z += 2) b.block(shelfX, 1, z, Material.CHISELED_BOOKSHELF, BlockRole.DECORATION, 2);
            }
        }
        if (b.level(3)) {
            b.floor(-6, 6, 6, 11, 0, Material.OAK_PLANKS, 3);
            b.room(-6, 6, 6, 11, 1, 5, Material.BRICKS, Material.STRIPPED_DARK_OAK_LOG, 3, 0);
            b.gableRoofX(-7, 7, 6, 12, 6, Material.DARK_OAK_PLANKS, 3);
            for (int x = -4; x <= 4; x += 2) b.block(x, 1, 9, Material.CHISELED_BOOKSHELF, BlockRole.DECORATION, 3);
        }
        if (b.level(4)) {
            b.tower(0, 9, 3, 12, Material.DEEPSLATE_TILES, Material.COPPER_BLOCK, 4);
            b.ring(-4, 4, 5, 13, 13, Material.CUT_COPPER, BlockRole.DECORATION, 4);
            b.block(0, 14, 9, Material.SEA_LANTERN, BlockRole.DECORATION, 4);
        }
        if (b.level(5)) {
            b.ring(-4, 4, -4, 4, 8, Material.DARK_OAK_LOG, BlockRole.RESIDENT, 5);
            b.ring(-3, 3, -3, 3, 9, Material.LAPIS_BLOCK, BlockRole.DECORATION, 5);
            b.ring(-2, 2, -2, 2, 10, Material.AMETHYST_BLOCK, BlockRole.DECORATION, 5);
            b.block(0, 11, 0, Material.ENCHANTING_TABLE, BlockRole.DECORATION, 5);
            for (int x : new int[]{-10, 10}) for (int z : new int[]{-4, 4}) b.block(x, 6, z, Material.SEA_LANTERN, BlockRole.DECORATION, 5);
        }
    }

    private void agrarian(Builder b) {
        if (b.level(1)) {
            b.floor(-4, 4, -3, 3, 0, Material.OAK_PLANKS, 1);
            b.room(-4, 4, -3, 3, 1, 4, Material.MUD_BRICKS, Material.STRIPPED_OAK_LOG, 1, 3);
            b.gableRoofX(-5, 5, -4, 4, 5, Material.SPRUCE_PLANKS, 1);
            for (int x : new int[]{-2, 0, 2}) b.block(x, 1, 1, Material.HAY_BLOCK, BlockRole.DECORATION, 1);
        }
        if (b.level(2)) {
            for (int side : new int[]{-1, 1}) {
                int x1 = side < 0 ? -12 : 6;
                int x2 = side < 0 ? -6 : 12;
                b.floor(x1, x2, -7, 6, 0, Material.DIRT, 2);
                b.ring(x1 - 1, x2 + 1, -8, 7, 1, Material.OAK_FENCE, BlockRole.RESIDENT, 2);
                for (int x = x1; x <= x2; x += 2) b.lineZ(-7, 6, 1, x, Material.HAY_BLOCK, BlockRole.DECORATION, 2);
            }
        }
        if (b.level(3)) {
            b.floor(-4, 4, 5, 11, 0, Material.STONE_BRICKS, 3);
            for (int x : new int[]{-4, 4}) for (int z : new int[]{5, 11}) b.pillar(x, z, 1, 4, Material.STRIPPED_OAK_LOG, 3);
            b.ring(-4, 4, 5, 11, 1, Material.GLASS, BlockRole.RESIDENT, 3);
            b.ring(-4, 4, 5, 11, 2, Material.GLASS, BlockRole.RESIDENT, 3);
            b.pyramidRoof(-5, 5, 4, 12, 5, Material.GLASS, 3);
            b.lineZ(6, 10, 1, 0, Material.MOSS_BLOCK, BlockRole.DECORATION, 3);
        }
        if (b.level(4)) {
            b.cylinder(8, 10, 3, 0, Material.STONE_BRICKS, BlockRole.RESIDENT, 4);
            b.cylinder(8, 10, 3, 1, Material.MUD_BRICKS, BlockRole.RESIDENT, 4);
            for (int y = 2; y <= 10; y++) b.ringCircle(8, 10, 3, y, Material.MUD_BRICKS, BlockRole.RESIDENT, 4);
            b.pyramidRoof(4, 12, 6, 14, 11, Material.SPRUCE_PLANKS, 4);
            b.block(8, 1, 10, Material.BARREL, BlockRole.DECORATION, 4);
        }
        if (b.level(5)) {
            b.pillar(-9, 10, 1, 12, Material.STRIPPED_SPRUCE_LOG, 5);
            b.lineX(-14, -4, 10, 10, Material.SPRUCE_PLANKS, BlockRole.RESIDENT, 5);
            for (int z = 5; z <= 15; z++) b.block(-9, 10, z, Material.SPRUCE_PLANKS, BlockRole.RESIDENT, 5);
            b.block(-9, 10, 10, Material.GOLD_BLOCK, BlockRole.DECORATION, 5);
            b.floor(-14, -10, 7, 13, 0, Material.OAK_PLANKS, 5);
            b.room(-14, -10, 7, 13, 1, 3, Material.MUD_BRICKS, Material.STRIPPED_OAK_LOG, 5, 0);
            b.gableRoofZ(-15, -9, 6, 14, 4, Material.SPRUCE_PLANKS, 5);
        }
    }

    /*
     * Архитектура 0.3: небольшие поселковые дома в духе ванильных деревень.
     * Каждый следующий этап образует понятную усадьбу из совместимых построек,
     * а не увеличивает одну стену до размеров монумента.
     */
    private void vanillaTownHall(Builder b) {
        if (b.level(1)) {
            b.villageHouse(-4, 4, -4, 3, 4, Material.COBBLESTONE, Material.OAK_PLANKS,
                    Material.OAK_PLANKS, Material.STRIPPED_OAK_LOG, Material.OAK_STAIRS,
                    Material.OAK_DOOR, 1, true);
            b.block(0, 1, 1, Material.LECTERN, BlockRole.DECORATION, 1);
            b.block(2, 1, 1, Material.BELL, BlockRole.DECORATION, 1);
        }
        if (b.level(2)) {
            b.villageHouse(6, 11, -3, 3, 3, Material.COBBLESTONE, Material.OAK_PLANKS,
                    Material.WHITE_TERRACOTTA, Material.STRIPPED_OAK_LOG, Material.OAK_STAIRS,
                    Material.OAK_DOOR, 2, false);
            b.path(4, 5, -1, 1, Material.COBBLESTONE, 2);
            b.seam(5, -3, 3, 3, Material.COBBLESTONE, Material.WHITE_TERRACOTTA,
                    Material.STRIPPED_OAK_LOG, 2);
            b.block(8, 1, 1, Material.LECTERN, BlockRole.DECORATION, 2);
        }
        if (b.level(3)) {
            b.villageHouse(-11, -6, -2, 4, 3, Material.COBBLESTONE, Material.OAK_PLANKS,
                    Material.STONE_BRICKS, Material.STRIPPED_OAK_LOG, Material.SPRUCE_STAIRS,
                    Material.OAK_DOOR, 3, false);
            b.path(-5, -4, 0, 2, Material.COBBLESTONE, 3);
            b.seam(-5, -2, 3, 3, Material.COBBLESTONE, Material.STONE_BRICKS,
                    Material.STRIPPED_OAK_LOG, 3);
            b.block(-8, 1, 1, Material.BOOKSHELF, BlockRole.DECORATION, 3);
        }
        if (b.level(4)) {
            b.villageTower(0, 8, 2, 7, Material.COBBLESTONE, Material.STRIPPED_OAK_LOG,
                    Material.OAK_STAIRS, Material.OAK_DOOR, 4);
            b.path(-1, 1, 4, 5, Material.COBBLESTONE, 4);
            b.block(0, 6, 6, Material.BELL, BlockRole.DECORATION, 4);
        }
        if (b.level(5)) {
            b.fencedYard(-7, 7, -11, -6, Material.OAK_FENCE, Material.OAK_FENCE_GATE,
                    Material.COBBLESTONE, 5);
            b.lamp(-5, -8, Material.OAK_FENCE, 5);
            b.lamp(5, -8, Material.OAK_FENCE, 5);
        }
    }

    private void vanillaForge(Builder b) {
        if (b.level(1)) {
            b.villageHouse(-4, 4, -4, 3, 3, Material.COBBLESTONE, Material.STONE,
                    Material.COBBLESTONE, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_STAIRS,
                    Material.SPRUCE_DOOR, 1, false);
            b.block(-2, 1, 1, Material.ANVIL, BlockRole.DECORATION, 1);
            b.block(1, 1, 1, Material.SMITHING_TABLE, BlockRole.DECORATION, 1);
            b.block(3, 1, 1, Material.BLAST_FURNACE, BlockRole.DECORATION, 1);
        }
        if (b.level(2)) {
            b.villageHouse(6, 11, -3, 3, 3, Material.COBBLESTONE, Material.STONE_BRICKS,
                    Material.BRICKS, Material.STRIPPED_SPRUCE_LOG, Material.BRICK_STAIRS,
                    Material.SPRUCE_DOOR, 2, false);
            b.path(4, 5, -1, 1, Material.COBBLESTONE, 2);
            b.seam(5, -3, 3, 3, Material.COBBLESTONE, Material.BRICKS,
                    Material.STRIPPED_SPRUCE_LOG, 2);
            b.block(8, 1, 1, Material.FURNACE, BlockRole.DECORATION, 2);
        }
        if (b.level(3)) {
            b.villageHouse(-11, -6, -2, 4, 3, Material.COBBLED_DEEPSLATE, Material.SPRUCE_PLANKS,
                    Material.COBBLED_DEEPSLATE, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_STAIRS,
                    Material.SPRUCE_DOOR, 3, false);
            b.seam(-5, -2, 3, 3, Material.COBBLED_DEEPSLATE, Material.COBBLED_DEEPSLATE,
                    Material.STRIPPED_SPRUCE_LOG, 3);
            b.block(-8, 1, 1, Material.BARREL, BlockRole.DECORATION, 3);
            b.block(-8, 1, 3, Material.RAW_IRON_BLOCK, BlockRole.DECORATION, 3);
        }
        if (b.level(4)) {
            b.canopy(-3, 3, 6, 10, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_STAIRS, 4);
            b.smallChimney(4, 9, 1, 7, Material.BRICKS, 4);
            b.block(0, 1, 8, Material.GRINDSTONE, BlockRole.DECORATION, 4);
            b.block(2, 1, 8, Material.ANVIL, BlockRole.DECORATION, 4);
        }
        if (b.level(5)) {
            b.villageHouse(6, 12, 6, 12, 3, Material.STONE_BRICKS, Material.SPRUCE_PLANKS,
                    Material.STONE_BRICKS, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_STAIRS,
                    Material.DARK_OAK_DOOR, 5, false);
            b.block(9, 1, 9, Material.SMITHING_TABLE, BlockRole.DECORATION, 5);
        }
    }

    private void vanillaBarracks(Builder b) {
        if (b.level(1)) {
            b.villageHouse(-5, 5, -4, 3, 3, Material.COBBLESTONE, Material.SPRUCE_PLANKS,
                    Material.STONE_BRICKS, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_STAIRS,
                    Material.SPRUCE_DOOR, 1, true);
            b.block(-3, 1, 1, Material.IRON_BARS, BlockRole.DECORATION, 1);
            b.block(3, 1, 1, Material.TARGET, BlockRole.DECORATION, 1);
        }
        if (b.level(2)) {
            b.villageHouse(7, 12, -3, 3, 3, Material.COBBLED_DEEPSLATE, Material.SPRUCE_PLANKS,
                    Material.COBBLED_DEEPSLATE, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_STAIRS,
                    Material.SPRUCE_DOOR, 2, false);
            b.seam(6, -3, 3, 3, Material.COBBLED_DEEPSLATE, Material.COBBLED_DEEPSLATE,
                    Material.STRIPPED_SPRUCE_LOG, 2);
            b.block(9, 1, 1, Material.SMITHING_TABLE, BlockRole.DECORATION, 2);
        }
        if (b.level(3)) {
            b.fencedYard(-12, -7, -5, 5, Material.SPRUCE_FENCE, Material.SPRUCE_FENCE_GATE,
                    Material.COARSE_DIRT, 3);
            b.block(-10, 1, -1, Material.TARGET, BlockRole.DECORATION, 3);
            b.block(-10, 1, 2, Material.HAY_BLOCK, BlockRole.DECORATION, 3);
        }
        if (b.level(4)) {
            b.villageTower(8, 9, 2, 6, Material.COBBLED_DEEPSLATE, Material.STRIPPED_SPRUCE_LOG,
                    Material.SPRUCE_STAIRS, Material.SPRUCE_DOOR, 4);
            b.lamp(5, 7, Material.SPRUCE_FENCE, 4);
        }
        if (b.level(5)) {
            b.villageHouse(-5, 2, 6, 12, 3, Material.COBBLESTONE, Material.SPRUCE_PLANKS,
                    Material.STONE_BRICKS, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_STAIRS,
                    Material.DARK_OAK_DOOR, 5, false);
            b.block(-2, 1, 9, Material.CARTOGRAPHY_TABLE, BlockRole.DECORATION, 5);
        }
    }

    private void vanillaMarket(Builder b) {
        if (b.level(1)) {
            b.villageHouse(-4, 4, -4, 3, 3, Material.COBBLESTONE, Material.OAK_PLANKS,
                    Material.YELLOW_TERRACOTTA, Material.STRIPPED_OAK_LOG, Material.OAK_STAIRS,
                    Material.OAK_DOOR, 1, false);
            b.block(-2, 1, 1, Material.BARREL, BlockRole.DECORATION, 1);
            b.block(2, 1, 1, Material.CHEST, BlockRole.DECORATION, 1);
        }
        if (b.level(2)) {
            b.stall(-8, -1, true, 2);
            b.stall(8, -1, true, 2);
        }
        if (b.level(3)) {
            b.stall(-8, 5, true, 3);
            b.stall(0, 6, true, 3);
            b.stall(8, 5, true, 3);
        }
        if (b.level(4)) {
            b.villageHouse(-5, 5, 10, 16, 3, Material.COBBLESTONE, Material.OAK_PLANKS,
                    Material.BRICKS, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_STAIRS,
                    Material.DARK_OAK_DOOR, 4, true);
            for (int x : new int[]{-3, 0, 3}) b.block(x, 1, 13, Material.BARREL, BlockRole.DECORATION, 4);
        }
        if (b.level(5)) {
            b.bellPavilion(0, -10, Material.STRIPPED_OAK_LOG, Material.OAK_STAIRS, 5);
            b.path(-1, 1, -8, -5, Material.COBBLESTONE, 5);
            b.lamp(-6, -8, Material.OAK_FENCE, 5);
            b.lamp(6, -8, Material.OAK_FENCE, 5);
        }
    }

    private void vanillaMinersGuild(Builder b) {
        if (b.level(1)) {
            b.villageHouse(-4, 4, -4, 3, 3, Material.COBBLED_DEEPSLATE, Material.SPRUCE_PLANKS,
                    Material.TUFF_BRICKS, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_STAIRS,
                    Material.DARK_OAK_DOOR, 1, false);
            b.block(-2, 1, 1, Material.CARTOGRAPHY_TABLE, BlockRole.DECORATION, 1);
            b.block(2, 1, 1, Material.BARREL, BlockRole.DECORATION, 1);
        }
        if (b.level(2)) {
            b.villageHouse(6, 11, -3, 3, 3, Material.COBBLED_DEEPSLATE, Material.SPRUCE_PLANKS,
                    Material.COBBLED_DEEPSLATE, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_STAIRS,
                    Material.DARK_OAK_DOOR, 2, false);
            b.seam(5, -3, 3, 3, Material.COBBLED_DEEPSLATE, Material.COBBLED_DEEPSLATE,
                    Material.STRIPPED_DARK_OAK_LOG, 2);
            b.block(8, 1, 1, Material.RAIL, BlockRole.DECORATION, 2);
        }
        if (b.level(3)) {
            b.villageHouse(-11, -6, -2, 4, 3, Material.TUFF_BRICKS, Material.SPRUCE_PLANKS,
                    Material.TUFF_BRICKS, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_STAIRS,
                    Material.SPRUCE_DOOR, 3, false);
            b.seam(-5, -2, 3, 3, Material.TUFF_BRICKS, Material.TUFF_BRICKS,
                    Material.STRIPPED_SPRUCE_LOG, 3);
            b.block(-8, 1, 1, Material.RAW_IRON_BLOCK, BlockRole.DECORATION, 3);
            b.block(-8, 1, 3, Material.RAW_COPPER_BLOCK, BlockRole.DECORATION, 3);
        }
        if (b.level(4)) {
            b.headframe(0, 9, Material.STRIPPED_DARK_OAK_LOG, 4);
            b.block(0, 1, 9, Material.RAIL, BlockRole.DECORATION, 4);
        }
        if (b.level(5)) {
            b.villageHouse(6, 12, 7, 13, 3, Material.COBBLED_DEEPSLATE, Material.SPRUCE_PLANKS,
                    Material.TUFF_BRICKS, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_STAIRS,
                    Material.DARK_OAK_DOOR, 5, false);
            b.block(9, 1, 10, Material.BLAST_FURNACE, BlockRole.DECORATION, 5);
        }
    }

    private void vanillaTemple(Builder b) {
        if (b.level(1)) {
            b.villageHouse(-3, 3, -6, 3, 5, Material.COBBLESTONE, Material.OAK_PLANKS,
                    Material.COBBLESTONE, Material.STRIPPED_OAK_LOG, Material.SPRUCE_STAIRS,
                    Material.OAK_DOOR, 1, false);
            b.block(0, 1, 1, Material.LECTERN, BlockRole.DECORATION, 1);
            b.block(0, 2, 2, Material.YELLOW_STAINED_GLASS_PANE, BlockRole.DECORATION, 1);
        }
        if (b.level(2)) {
            b.villageHouse(5, 10, -2, 4, 3, Material.COBBLESTONE, Material.OAK_PLANKS,
                    Material.WHITE_TERRACOTTA, Material.STRIPPED_OAK_LOG, Material.OAK_STAIRS,
                    Material.OAK_DOOR, 2, false);
            b.seam(4, -2, 3, 3, Material.COBBLESTONE, Material.WHITE_TERRACOTTA,
                    Material.STRIPPED_OAK_LOG, 2);
            b.block(7, 1, 1, Material.BOOKSHELF, BlockRole.DECORATION, 2);
        }
        if (b.level(3)) {
            b.villageHouse(-10, -5, -2, 4, 3, Material.COBBLESTONE, Material.OAK_PLANKS,
                    Material.OAK_PLANKS, Material.STRIPPED_OAK_LOG, Material.OAK_STAIRS,
                    Material.OAK_DOOR, 3, false);
            b.seam(-4, -2, 3, 3, Material.COBBLESTONE, Material.OAK_PLANKS,
                    Material.STRIPPED_OAK_LOG, 3);
            b.block(-7, 1, 1, Material.WHITE_WOOL, BlockRole.DECORATION, 3);
        }
        if (b.level(4)) {
            b.villageTower(0, 9, 2, 8, Material.COBBLESTONE, Material.STRIPPED_OAK_LOG,
                    Material.SPRUCE_STAIRS, Material.OAK_DOOR, 4);
            b.block(0, 7, 7, Material.BELL, BlockRole.DECORATION, 4);
        }
        if (b.level(5)) {
            b.fencedYard(-11, 11, 6, 14, Material.COBBLESTONE_WALL, Material.OAK_FENCE_GATE,
                    Material.MOSS_BLOCK, 5);
            for (int x : new int[]{-8, -5, 5, 8}) b.block(x, 1, 10, Material.ALLIUM, BlockRole.DECORATION, 5);
            b.lamp(-9, 7, Material.COBBLESTONE_WALL, 5);
            b.lamp(9, 7, Material.COBBLESTONE_WALL, 5);
        }
    }

    private void vanillaLibrary(Builder b) {
        if (b.level(1)) {
            b.villageHouse(-4, 4, -4, 4, 4, Material.COBBLESTONE, Material.OAK_PLANKS,
                    Material.OAK_PLANKS, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_STAIRS,
                    Material.DARK_OAK_DOOR, 1, false);
            for (int x : new int[]{-2, 2}) b.block(x, 1, 2, Material.BOOKSHELF, BlockRole.DECORATION, 1);
            b.block(0, 1, 2, Material.LECTERN, BlockRole.DECORATION, 1);
        }
        if (b.level(2)) {
            b.villageHouse(6, 12, -3, 4, 3, Material.COBBLESTONE, Material.OAK_PLANKS,
                    Material.OAK_PLANKS, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_STAIRS,
                    Material.DARK_OAK_DOOR, 2, false);
            b.seam(5, -3, 3, 3, Material.COBBLESTONE, Material.OAK_PLANKS,
                    Material.STRIPPED_DARK_OAK_LOG, 2);
            b.block(9, 1, 1, Material.CHISELED_BOOKSHELF, BlockRole.DECORATION, 2);
        }
        if (b.level(3)) {
            b.villageHouse(-12, -6, -3, 4, 3, Material.STONE_BRICKS, Material.OAK_PLANKS,
                    Material.BRICKS, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_STAIRS,
                    Material.DARK_OAK_DOOR, 3, false);
            b.seam(-5, -3, 3, 3, Material.STONE_BRICKS, Material.BRICKS,
                    Material.STRIPPED_DARK_OAK_LOG, 3);
            b.block(-9, 1, 1, Material.CHISELED_BOOKSHELF, BlockRole.DECORATION, 3);
        }
        if (b.level(4)) {
            b.villageTower(0, 10, 2, 7, Material.BRICKS, Material.STRIPPED_DARK_OAK_LOG,
                    Material.DARK_OAK_STAIRS, Material.DARK_OAK_DOOR, 4);
            b.block(0, 6, 8, Material.CARTOGRAPHY_TABLE, BlockRole.DECORATION, 4);
        }
        if (b.level(5)) {
            b.fencedYard(-7, 7, -11, -6, Material.DARK_OAK_FENCE, Material.DARK_OAK_FENCE_GATE,
                    Material.COBBLESTONE, 5);
            b.block(-3, 1, -8, Material.LECTERN, BlockRole.DECORATION, 5);
            b.block(3, 1, -8, Material.ENCHANTING_TABLE, BlockRole.DECORATION, 5);
            b.lamp(0, -7, Material.DARK_OAK_FENCE, 5);
        }
    }

    private void vanillaAgrarian(Builder b) {
        if (b.level(1)) {
            b.villageHouse(-4, 4, -4, 3, 3, Material.COBBLESTONE, Material.OAK_PLANKS,
                    Material.MUD_BRICKS, Material.STRIPPED_OAK_LOG, Material.SPRUCE_STAIRS,
                    Material.OAK_DOOR, 1, false);
            b.block(-2, 1, 1, Material.HAY_BLOCK, BlockRole.DECORATION, 1);
            b.block(2, 1, 1, Material.COMPOSTER, BlockRole.DECORATION, 1);
        }
        if (b.level(2)) {
            b.villageHouse(6, 13, -5, 4, 4, Material.COBBLESTONE, Material.OAK_PLANKS,
                    Material.OAK_PLANKS, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_STAIRS,
                    Material.SPRUCE_DOOR, 2, true);
            b.seam(5, -4, 3, 3, Material.COBBLESTONE, Material.OAK_PLANKS,
                    Material.STRIPPED_SPRUCE_LOG, 2);
            for (int z : new int[]{-1, 2}) b.block(9, 1, z, Material.HAY_BLOCK, BlockRole.DECORATION, 2);
        }
        if (b.level(3)) {
            b.greenhouse(-12, -6, -3, 5, Material.STRIPPED_OAK_LOG, 3);
            b.block(-9, 1, 1, Material.COMPOSTER, BlockRole.DECORATION, 3);
        }
        if (b.level(4)) {
            b.villageHouse(-4, 3, 7, 13, 3, Material.COBBLESTONE, Material.OAK_PLANKS,
                    Material.MUD_BRICKS, Material.STRIPPED_OAK_LOG, Material.SPRUCE_STAIRS,
                    Material.OAK_DOOR, 4, false);
            b.block(-1, 1, 10, Material.BARREL, BlockRole.DECORATION, 4);
        }
        if (b.level(5)) {
            b.windmill(9, 11, Material.COBBLESTONE, Material.STRIPPED_SPRUCE_LOG,
                    Material.SPRUCE_STAIRS, 5);
            b.block(9, 1, 11, Material.HAY_BLOCK, BlockRole.DECORATION, 5);
        }
    }

    private static final class Builder {
        private final int targetLevel;
        private final int architectureVersion;
        private final Map<BlockOffset, BlueprintBlock> blocks = new LinkedHashMap<>();

        private Builder(int targetLevel, int architectureVersion) {
            this.targetLevel = targetLevel;
            this.architectureVersion = architectureVersion;
        }
        private boolean level(int stage) { return targetLevel >= stage; }

        private void block(int x, int y, int z, Material material, BlockRole role, int stage) {
            if (!level(stage) || material == Material.AIR) return;
            blocks.putIfAbsent(new BlockOffset(x, y, z), new BlueprintBlock(material, role, stage));
        }

        private void axisBlock(int x, int y, int z, Material material, BlockRole role, int stage, Axis axis) {
            if (!level(stage) || material == Material.AIR) return;
            blocks.putIfAbsent(new BlockOffset(x, y, z), new BlueprintBlock(material, role, stage).withAxis(axis));
        }

        private void accent(int x, int y, int z, Material material, BlockRole role, int stage) {
            if (!level(stage) || material == Material.AIR) return;
            BlockOffset offset = new BlockOffset(x, y, z);
            BlueprintBlock existing = blocks.get(offset);
            if (existing == null || existing.stage() == stage) {
                blocks.put(offset, new BlueprintBlock(material, role, stage));
            }
        }

        private void clear(int x, int y, int z, int stage) {
            if (!level(stage)) return;
            BlockOffset offset = new BlockOffset(x, y, z);
            BlueprintBlock existing = blocks.get(offset);
            if (existing != null && existing.stage() == stage) blocks.remove(offset);
        }

        private void floor(int x1, int x2, int z1, int z2, int y, Material material, int stage) {
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++)
                block(x, y, z, material, BlockRole.RESIDENT, stage);
        }

        private void lineX(int x1, int x2, int y, int z, Material material, BlockRole role, int stage) {
            for (int x = x1; x <= x2; x++) axisBlock(x, y, z, material, role, stage, Axis.X);
        }

        private void lineZ(int z1, int z2, int y, int x, Material material, BlockRole role, int stage) {
            for (int z = z1; z <= z2; z++) axisBlock(x, y, z, material, role, stage, Axis.Z);
        }

        private void pillar(int x, int z, int y1, int y2, Material material, int stage) {
            for (int y = y1; y <= y2; y++) axisBlock(x, y, z, material, BlockRole.RESIDENT, stage, Axis.Y);
        }

        private void accentPillar(int x, int z, int y1, int y2, Material material, int stage) {
            for (int y = y1; y <= y2; y++) accent(x, y, z, material, BlockRole.RESIDENT, stage);
        }

        private void ring(int x1, int x2, int z1, int z2, int y, Material material, BlockRole role, int stage) {
            lineX(x1, x2, y, z1, material, role, stage);
            lineX(x1, x2, y, z2, material, role, stage);
            lineZ(z1 + 1, z2 - 1, y, x1, material, role, stage);
            lineZ(z1 + 1, z2 - 1, y, x2, material, role, stage);
        }

        private void accentRing(int x1, int x2, int z1, int z2, int y, Material material,
                                BlockRole role, int stage) {
            for (int x = x1; x <= x2; x++) {
                accent(x, y, z1, material, role, stage);
                accent(x, y, z2, material, role, stage);
            }
            for (int z = z1 + 1; z < z2; z++) {
                accent(x1, y, z, material, role, stage);
                accent(x2, y, z, material, role, stage);
            }
        }

        private void room(int x1, int x2, int z1, int z2, int y1, int y2, Material wall,
                          Material frame, int stage, int doorHalfWidth) {
            int doorWidth = doorHalfWidth <= 0 ? 0 : Math.min(2, doorHalfWidth);
            int center = (x1 + x2) / 2;
            int firstDoorX = doorWidth == 2 ? center - 1 : center;
            int secondDoorX = center;
            for (int y = y1; y <= y2; y++) {
                for (int x = x1; x <= x2; x++) {
                    boolean doorway = doorWidth > 0 && y <= y1 + 1
                            && (x == firstDoorX || (doorWidth == 2 && x == secondDoorX));
                    if (!doorway) block(x, y, z1, wall, BlockRole.RESIDENT, stage);
                    block(x, y, z2, wall, BlockRole.RESIDENT, stage);
                }
                for (int z = z1 + 1; z < z2; z++) {
                    block(x1, y, z, wall, BlockRole.RESIDENT, stage);
                    block(x2, y, z, wall, BlockRole.RESIDENT, stage);
                }
            }
            for (int x : new int[]{x1, x2}) for (int z : new int[]{z1, z2}) accentPillar(x, z, y1, y2, frame, stage);
            for (int x = x1 + 2; x <= x2 - 2; x += 3) {
                accent(x, Math.min(y2, y1 + 1), z2, Material.GLASS_PANE, BlockRole.DECORATION, stage);
            }
            if (doorWidth > 0) {
                Material door = doorMaterial(frame);
                if (doorWidth == 1) {
                    door(firstDoorX, y1, z1, door, Door.Hinge.LEFT, stage);
                } else {
                    door(firstDoorX, y1, z1, door, Door.Hinge.LEFT, stage);
                    door(secondDoorX, y1, z1, door, Door.Hinge.RIGHT, stage);
                }
            }
        }

        private void door(int x, int y, int z, Material material, Door.Hinge hinge, int stage) {
            door(x, y, z, material, BlockFace.NORTH, hinge, stage);
        }

        private void door(int x, int y, int z, Material material, BlockFace facing, Door.Hinge hinge, int stage) {
            if (!level(stage)) return;
            blocks.putIfAbsent(new BlockOffset(x, y, z),
                    new BlueprintBlock(material, BlockRole.DECORATION, stage).asDoor(facing, Bisected.Half.BOTTOM, hinge));
            blocks.putIfAbsent(new BlockOffset(x, y + 1, z),
                    new BlueprintBlock(material, BlockRole.DECORATION, stage).asDoor(facing, Bisected.Half.TOP, hinge));
        }

        private Material doorMaterial(Material frame) {
            return switch (frame) {
                case STRIPPED_SPRUCE_LOG, SPRUCE_LOG -> Material.SPRUCE_DOOR;
                case STRIPPED_DARK_OAK_LOG, DARK_OAK_LOG -> Material.DARK_OAK_DOOR;
                case QUARTZ_PILLAR -> Material.BIRCH_DOOR;
                case IRON_BLOCK -> Material.COPPER_DOOR;
                default -> Material.OAK_DOOR;
            };
        }

        private void stair(int x, int y, int z, Material material, BlockFace facing,
                           Bisected.Half half, BlockRole role, int stage) {
            if (!level(stage) || material == Material.AIR) return;
            blocks.putIfAbsent(new BlockOffset(x, y, z),
                    new BlueprintBlock(material, role, stage).asStair(facing, half));
        }

        private void replaceStair(int x, int y, int z, Material material, BlockFace facing,
                                  Bisected.Half half, BlockRole role, int stage) {
            if (!level(stage) || material == Material.AIR) return;
            BlockOffset offset = new BlockOffset(x, y, z);
            BlueprintBlock existing = blocks.get(offset);
            if (existing == null || existing.stage() == stage) {
                blocks.put(offset, new BlueprintBlock(material, role, stage).asStair(facing, half));
            }
        }

        private void ellipseDisc(int cx, int cz, int radiusX, int radiusZ, int y,
                                 Material material, BlockRole role, int stage) {
            for (int x = -radiusX; x <= radiusX; x++) for (int z = -radiusZ; z <= radiusZ; z++) {
                if (insideEllipse(x, z, radiusX, radiusZ)) block(cx + x, y, cz + z, material, role, stage);
            }
        }

        private void ellipseRing(int cx, int cz, int radiusX, int radiusZ, int y,
                                 Material material, BlockRole role, int stage) {
            int innerX = Math.max(0, radiusX - 1);
            int innerZ = Math.max(0, radiusZ - 1);
            for (int x = -radiusX; x <= radiusX; x++) for (int z = -radiusZ; z <= radiusZ; z++) {
                if (insideEllipse(x, z, radiusX, radiusZ)
                        && !insideEllipse(x, z, innerX, innerZ)) {
                    block(cx + x, y, cz + z, material, role, stage);
                }
            }
        }

        private void ellipseSeatRing(int cx, int cz, int radiusX, int radiusZ, int y,
                                     Material material, int stage) {
            int innerX = Math.max(0, radiusX - 1);
            int innerZ = Math.max(0, radiusZ - 1);
            for (int x = -radiusX; x <= radiusX; x++) for (int z = -radiusZ; z <= radiusZ; z++) {
                if (!insideEllipse(x, z, radiusX, radiusZ) || insideEllipse(x, z, innerX, innerZ)) continue;
                BlockFace facing;
                double horizontal = Math.abs((double) x / Math.max(1, radiusX));
                double vertical = Math.abs((double) z / Math.max(1, radiusZ));
                if (horizontal >= vertical) facing = x >= 0 ? BlockFace.WEST : BlockFace.EAST;
                else facing = z >= 0 ? BlockFace.NORTH : BlockFace.SOUTH;
                replaceStair(cx + x, y, cz + z, material, facing, Bisected.Half.BOTTOM,
                        BlockRole.DECORATION, stage);
            }
        }

        private boolean insideEllipse(int x, int z, int radiusX, int radiusZ) {
            if (radiusX <= 0 || radiusZ <= 0) return false;
            double normalizedX = (double) x * x / (radiusX * radiusX);
            double normalizedZ = (double) z * z / (radiusZ * radiusZ);
            return normalizedX + normalizedZ <= 1.0;
        }

        private void ellipsePillars(int cx, int cz, int radiusX, int radiusZ, int count,
                                    int y1, int y2, Material material, int stage) {
            Set<BlockOffset> points = new java.util.LinkedHashSet<>();
            for (int index = 0; index < count; index++) {
                double angle = index * Math.PI * 2.0 / count;
                int x = cx + (int) Math.round(Math.cos(angle) * radiusX);
                int z = cz + (int) Math.round(Math.sin(angle) * radiusZ);
                points.add(new BlockOffset(x, 0, z));
            }
            for (BlockOffset point : points) pillar(point.x(), point.z(), y1, y2, material, stage);
        }

        private void taperedSquareTower(int x1, int x2, int z1, int z2, int y1, int y2,
                                        Material wall, Material frame, int stage) {
            for (int y = y1; y <= y2; y++) ring(x1, x2, z1, z2, y, wall, BlockRole.RESIDENT, stage);
            for (int x : new int[]{x1, x2}) for (int z : new int[]{z1, z2}) {
                accentPillar(x, z, y1, y2, frame, stage);
            }
        }

        private void spiralStairs(int y1, int y2, Material material, int stage) {
            int[][] loop = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
            for (int y = y1; y <= y2; y++) {
                int index = (y - y1) % loop.length;
                int next = (index + 1) % loop.length;
                int x = loop[index][0];
                int z = loop[index][1];
                int dx = loop[next][0] - x;
                int dz = loop[next][1] - z;
                BlockFace facing = dx > 0 ? BlockFace.EAST : dx < 0 ? BlockFace.WEST
                        : dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
                replaceStair(x, y, z, material, facing, Bisected.Half.BOTTOM,
                        BlockRole.DECORATION, stage);
            }
        }

        private void gardenColonnade(int x1, int x2, int z1, int z2, int y1, int y2,
                                     Material lintel, Material columns, int stage) {
            ring(x1, x2, z1, z2, y1, lintel, BlockRole.RESIDENT, stage);
            for (int x = x1; x <= x2; x += 4) {
                pillar(x, z1, y1 + 1, y2, columns, stage);
                pillar(x, z2, y1 + 1, y2, columns, stage);
            }
            for (int z = z1; z <= z2; z += 4) {
                pillar(x1, z, y1 + 1, y2, columns, stage);
                pillar(x2, z, y1 + 1, y2, columns, stage);
            }
            pillar(x2, z2, y1 + 1, y2, columns, stage);
            ring(x1, x2, z1, z2, y2, lintel, BlockRole.DECORATION, stage);
        }

        private void terraceStairs(int x1, int x2, int z1, int z2, int firstY,
                                   Material material, int stage) {
            int direction = Integer.compare(z2, z1);
            int steps = Math.abs(z2 - z1);
            for (int step = 0; step <= steps; step++) {
                int z = z1 + direction * step;
                for (int x = x1; x <= x2; x++) {
                    replaceStair(x, firstY + step, z, material,
                            direction >= 0 ? BlockFace.SOUTH : BlockFace.NORTH,
                            Bisected.Half.BOTTOM, BlockRole.DECORATION, stage);
                }
            }
        }

        private void gardenEdge(int x1, int x2, int z1, int z2, int y, int spacing, int stage) {
            int index = 0;
            for (int x = x1; x <= x2; x += spacing) {
                gardenPlant(x, y, z1, index++, stage);
                gardenPlant(x, y, z2, index++, stage);
            }
            for (int z = z1 + spacing; z < z2; z += spacing) {
                gardenPlant(x1, y, z, index++, stage);
                gardenPlant(x2, y, z, index++, stage);
            }
        }

        private void gardenPlant(int x, int y, int z, int index, int stage) {
            accent(x, y - 1, z, Material.MOSS_BLOCK, BlockRole.DECORATION, stage);
            block(x, y, z, index % 3 == 0 ? Material.FLOWERING_AZALEA : Material.AZALEA,
                    BlockRole.DECORATION, stage);
            if (index % 2 == 0) {
                block(x + (index % 4 == 0 ? 1 : -1), y, z, Material.MOSS_CARPET,
                        BlockRole.DECORATION, stage);
            }
        }

        private void magicWindows(int radius, int y, Material material, int stage) {
            for (int[] point : new int[][]{{0, -radius}, {radius, 0}, {0, radius}, {-radius, 0}}) {
                accent(point[0], y, point[1], material, BlockRole.DECORATION, stage);
                accent(point[0], y + 1, point[1], material, BlockRole.DECORATION, stage);
            }
        }

        private void crystalCrown(int radius, int baseY, int height, int stage) {
            int diagonal = Math.max(1, (int) Math.round(radius * 0.7));
            int[][] points = {{radius, 0}, {-radius, 0}, {0, radius}, {0, -radius},
                    {diagonal, diagonal}, {-diagonal, diagonal}, {diagonal, -diagonal}, {-diagonal, -diagonal}};
            for (int index = 0; index < points.length; index++) {
                int localHeight = Math.max(2, height - index % 3);
                for (int dy = 0; dy < localHeight; dy++) {
                    accent(points[index][0], baseY + dy, points[index][1], Material.AMETHYST_BLOCK,
                            BlockRole.DECORATION, stage);
                }
                block(points[index][0], baseY + localHeight, points[index][1], Material.AMETHYST_CLUSTER,
                        BlockRole.DECORATION, stage);
            }
        }

        private void villageHouse(int x1, int x2, int z1, int z2, int wallTop,
                                  Material foundation, Material floorMaterial, Material wall,
                                  Material frame, Material roofStair, Material doorMaterial,
                                  int stage, boolean doubleDoor) {
            floor(x1, x2, z1, z2, 0, floorMaterial, stage);
            ring(x1, x2, z1, z2, 0, foundation, BlockRole.RESIDENT, stage);
            int center = (x1 + x2) / 2;
            int leftDoor = doubleDoor ? center - 1 : center;
            int rightDoor = center;
            for (int y = 1; y <= wallTop; y++) {
                for (int x = x1; x <= x2; x++) {
                    boolean entrance = y <= 2 && (x == leftDoor || (doubleDoor && x == rightDoor));
                    if (!entrance) block(x, y, z1, wall, BlockRole.RESIDENT, stage);
                    block(x, y, z2, wall, BlockRole.RESIDENT, stage);
                }
                for (int z = z1 + 1; z < z2; z++) {
                    block(x1, y, z, wall, BlockRole.RESIDENT, stage);
                    block(x2, y, z, wall, BlockRole.RESIDENT, stage);
                }
            }
            for (int x : new int[]{x1, x2}) for (int z : new int[]{z1, z2}) {
                accentPillar(x, z, 1, wallTop, frame, stage);
            }

            int windowY = Math.min(wallTop, 2);
            int leftWindow = Math.min(x2 - 1, x1 + 2);
            int rightWindow = Math.max(x1 + 1, x2 - 2);
            for (int x : new int[]{leftWindow, rightWindow}) {
                if (x != leftDoor && (!doubleDoor || x != rightDoor)) {
                    accent(x, windowY, z1, Material.GLASS_PANE, BlockRole.DECORATION, stage);
                }
                accent(x, windowY, z2, Material.GLASS_PANE, BlockRole.DECORATION, stage);
            }
            int middleZ = (z1 + z2) / 2;
            accent(x1, windowY, middleZ, Material.GLASS_PANE, BlockRole.DECORATION, stage);
            accent(x2, windowY, middleZ, Material.GLASS_PANE, BlockRole.DECORATION, stage);

            if (doubleDoor) {
                door(leftDoor, 1, z1, doorMaterial, BlockFace.NORTH, Door.Hinge.LEFT, stage);
                door(rightDoor, 1, z1, doorMaterial, BlockFace.NORTH, Door.Hinge.RIGHT, stage);
            } else {
                door(center, 1, z1, doorMaterial, BlockFace.NORTH, Door.Hinge.LEFT, stage);
            }
            for (int x = leftDoor; x <= rightDoor; x++) {
                block(x, 0, z1 - 1, foundation, BlockRole.RESIDENT, stage);
            }
            vanillaGableRoof(x1, x2, z1, z2, wallTop, wall, roofStair, stage);
        }

        private void vanillaGableRoof(int x1, int x2, int z1, int z2, int wallTop,
                                      Material gableWall, Material roofStair, int stage) {
            int left = x1 - 1;
            int right = x2 + 1;
            int layer = 0;
            while (left + layer < right - layer) {
                int y = wallTop + 1 + layer;
                for (int z = z1 - 1; z <= z2 + 1; z++) {
                    stair(left + layer, y, z, roofStair, BlockFace.EAST,
                            Bisected.Half.BOTTOM, BlockRole.DECORATION, stage);
                    stair(right - layer, y, z, roofStair, BlockFace.WEST,
                            Bisected.Half.BOTTOM, BlockRole.DECORATION, stage);
                }
                layer++;
            }
            int ridgeX = (left + right) / 2;
            int ridgeY = architectureVersion <= 2 ? wallTop + 1 + layer : wallTop + layer;
            Material ridge = roofBlock(roofStair);
            int ridgeStart = architectureVersion == 3 ? z1 : z1 - 1;
            int ridgeEnd = architectureVersion == 3 ? z2 : z2 + 1;
            for (int z = ridgeStart; z <= ridgeEnd; z++) {
                axisBlock(ridgeX, ridgeY, z, ridge, BlockRole.DECORATION, stage, Axis.Z);
            }
            for (int x = x1; x <= x2; x++) {
                int top = wallTop + Math.min(x - left, right - x);
                for (int y = wallTop + 1; y <= top; y++) {
                    block(x, y, z1, gableWall, BlockRole.DECORATION, stage);
                    block(x, y, z2, gableWall, BlockRole.DECORATION, stage);
                }
            }
        }

        private Material roofBlock(Material stair) {
            return switch (stair) {
                case OAK_STAIRS -> Material.OAK_PLANKS;
                case SPRUCE_STAIRS -> Material.SPRUCE_PLANKS;
                case DARK_OAK_STAIRS -> Material.DARK_OAK_PLANKS;
                case BIRCH_STAIRS -> Material.BIRCH_PLANKS;
                case BRICK_STAIRS -> Material.BRICKS;
                case STONE_BRICK_STAIRS -> Material.STONE_BRICKS;
                case COBBLED_DEEPSLATE_STAIRS -> Material.COBBLED_DEEPSLATE;
                case DEEPSLATE_BRICK_STAIRS -> Material.DEEPSLATE_BRICKS;
                case DEEPSLATE_TILE_STAIRS -> Material.DEEPSLATE_TILES;
                default -> Material.OAK_PLANKS;
            };
        }

        private void path(int x1, int x2, int z1, int z2, Material material, int stage) {
            floor(Math.min(x1, x2), Math.max(x1, x2), Math.min(z1, z2), Math.max(z1, z2),
                    0, material, stage);
        }

        private void seam(int x, int z1, int z2, int wallTop, Material foundation,
                          Material wall, Material frame, int stage) {
            if (architectureVersion < 4 || !level(stage)) return;
            for (int z = z1; z <= z2; z++) {
                block(x, 0, z, foundation, BlockRole.DECORATION, stage);
                for (int y = 1; y <= wallTop; y++) {
                    block(x, y, z, wall, BlockRole.DECORATION, stage);
                }
            }
            for (int y = 1; y <= wallTop; y++) {
                accent(x, y, z1, frame, BlockRole.DECORATION, stage);
                accent(x, y, z2, frame, BlockRole.DECORATION, stage);
            }
        }

        private void fencedYard(int x1, int x2, int z1, int z2, Material fence, Material gate,
                                Material ground, int stage) {
            floor(x1 + 1, x2 - 1, z1 + 1, z2 - 1, 0, ground, stage);
            ring(x1, x2, z1, z2, 1, fence, BlockRole.RESIDENT, stage);
            int center = (x1 + x2) / 2;
            accent(center, 1, z1, gate, BlockRole.DECORATION, stage);
            block(center, 0, z1 - 1, ground, BlockRole.RESIDENT, stage);
        }

        private void lamp(int x, int z, Material post, int stage) {
            pillar(x, z, 1, 2, post, stage);
            block(x, 3, z, Material.LANTERN, BlockRole.DECORATION, stage);
        }

        private void villageTower(int centerX, int centerZ, int radius, int wallTop,
                                  Material wall, Material frame, Material roofStair,
                                  Material doorMaterial, int stage) {
            int x1 = centerX - radius, x2 = centerX + radius;
            int z1 = centerZ - radius, z2 = centerZ + radius;
            floor(x1, x2, z1, z2, 0, Material.COBBLESTONE, stage);
            int doorX = centerX;
            for (int y = 1; y <= wallTop; y++) {
                for (int x = x1; x <= x2; x++) {
                    if (!(x == doorX && y <= 2)) block(x, y, z1, wall, BlockRole.RESIDENT, stage);
                    block(x, y, z2, wall, BlockRole.RESIDENT, stage);
                }
                for (int z = z1 + 1; z < z2; z++) {
                    block(x1, y, z, wall, BlockRole.RESIDENT, stage);
                    block(x2, y, z, wall, BlockRole.RESIDENT, stage);
                }
            }
            for (int x : new int[]{x1, x2}) for (int z : new int[]{z1, z2}) accentPillar(x, z, 1, wallTop, frame, stage);
            int windowY = Math.max(3, wallTop - 1);
            accent(centerX, windowY, z1, Material.GLASS_PANE, BlockRole.DECORATION, stage);
            accent(centerX, windowY, z2, Material.GLASS_PANE, BlockRole.DECORATION, stage);
            accent(x1, windowY, centerZ, Material.GLASS_PANE, BlockRole.DECORATION, stage);
            accent(x2, windowY, centerZ, Material.GLASS_PANE, BlockRole.DECORATION, stage);
            door(doorX, 1, z1, doorMaterial, BlockFace.NORTH, Door.Hinge.LEFT, stage);
            vanillaGableRoof(x1, x2, z1, z2, wallTop, wall, roofStair, stage);
        }

        private void canopy(int x1, int x2, int z1, int z2, Material frame,
                            Material roofStair, int stage) {
            floor(x1, x2, z1, z2, 0, Material.COBBLESTONE, stage);
            for (int x : new int[]{x1, x2}) for (int z : new int[]{z1, z2}) pillar(x, z, 1, 3, frame, stage);
            Material roof = roofBlock(roofStair);
            flatRoof(x1 - 1, x2 + 1, z1 - 1, z2 + 1, 4, roof, stage);
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                stair(x, 4, z1 - 1, roofStair, BlockFace.NORTH, Bisected.Half.BOTTOM, BlockRole.DECORATION, stage);
                stair(x, 4, z2 + 1, roofStair, BlockFace.SOUTH, Bisected.Half.BOTTOM, BlockRole.DECORATION, stage);
            }
        }

        private void smallChimney(int x, int z, int y1, int y2, Material material, int stage) {
            pillar(x, z, y1, y2, material, stage);
            block(x, y2 + 1, z, Material.CAMPFIRE, BlockRole.DECORATION, stage);
        }

        private void bellPavilion(int centerX, int centerZ, Material frame, Material roofStair, int stage) {
            floor(centerX - 2, centerX + 2, centerZ - 2, centerZ + 2, 0, Material.COBBLESTONE, stage);
            for (int x : new int[]{centerX - 2, centerX + 2}) for (int z : new int[]{centerZ - 2, centerZ + 2}) {
                pillar(x, z, 1, 3, frame, stage);
            }
            vanillaGableRoof(centerX - 2, centerX + 2, centerZ - 2, centerZ + 2,
                    3, Material.OAK_PLANKS, roofStair, stage);
            block(centerX, 2, centerZ, Material.BELL, BlockRole.DECORATION, stage);
        }

        private void headframe(int centerX, int centerZ, Material frame, int stage) {
            for (int x : new int[]{centerX - 2, centerX + 2}) pillar(x, centerZ, 0, 7, frame, stage);
            lineX(centerX - 3, centerX + 3, 8, centerZ, frame, BlockRole.RESIDENT, stage);
            for (int y = 3; y <= 7; y++) {
                block(centerX, y, centerZ, Material.IRON_CHAIN, BlockRole.DECORATION, stage);
            }
            block(centerX, 2, centerZ, Material.IRON_BLOCK, BlockRole.DECORATION, stage);
            floor(centerX - 3, centerX + 3, centerZ - 2, centerZ + 2, 0, Material.COBBLED_DEEPSLATE, stage);
        }

        private void greenhouse(int x1, int x2, int z1, int z2, Material frame, int stage) {
            floor(x1, x2, z1, z2, 0, Material.MOSS_BLOCK, stage);
            for (int y = 1; y <= 3; y++) {
                for (int x = x1; x <= x2; x++) {
                    if (!(x == (x1 + x2) / 2 && y <= 2)) block(x, y, z1, Material.GLASS, BlockRole.RESIDENT, stage);
                    block(x, y, z2, Material.GLASS, BlockRole.RESIDENT, stage);
                }
                for (int z = z1 + 1; z < z2; z++) {
                    block(x1, y, z, Material.GLASS, BlockRole.RESIDENT, stage);
                    block(x2, y, z, Material.GLASS, BlockRole.RESIDENT, stage);
                }
            }
            for (int x : new int[]{x1, x2}) for (int z : new int[]{z1, z2}) accentPillar(x, z, 1, 3, frame, stage);
            gableRoofZ(x1 - 1, x2 + 1, z1 - 1, z2 + 1, 4, Material.GLASS, stage);
            door((x1 + x2) / 2, 1, z1, Material.OAK_DOOR, BlockFace.NORTH, Door.Hinge.LEFT, stage);
        }

        private void windmill(int centerX, int centerZ, Material wall, Material frame,
                              Material roofStair, int stage) {
            villageTower(centerX, centerZ, 2, 6, wall, frame, roofStair, Material.SPRUCE_DOOR, stage);
            int bladeZ = centerZ - 3;
            for (int offset = -3; offset <= 3; offset++) {
                block(centerX + offset, 5, bladeZ, Material.SPRUCE_PLANKS, BlockRole.DECORATION, stage);
                block(centerX, 5 + offset, bladeZ, Material.SPRUCE_PLANKS, BlockRole.DECORATION, stage);
            }
            block(centerX, 5, bladeZ, Material.STRIPPED_SPRUCE_LOG, BlockRole.DECORATION, stage);
        }

        private void flatRoof(int x1, int x2, int z1, int z2, int y, Material material, int stage) {
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++)
                block(x, y, z, material, BlockRole.DECORATION, stage);
        }

        private void pyramidRoof(int x1, int x2, int z1, int z2, int y, Material material, int stage) {
            int layer = 0;
            while (x1 + layer <= x2 - layer && z1 + layer <= z2 - layer) {
                ring(x1 + layer, x2 - layer, z1 + layer, z2 - layer, y + layer, material, BlockRole.DECORATION, stage);
                layer++;
            }
        }

        private void hipRoof(int x1, int x2, int z1, int z2, int y, Material material, int stage) {
            pyramidRoof(x1, x2, z1, z2, y, material, stage);
        }

        private void gableRoofX(int x1, int x2, int z1, int z2, int y, Material material, int stage) {
            int depth = z2 - z1;
            int half = depth / 2;
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
                int rise = Math.min(z - z1, z2 - z);
                block(x, y + Math.min(half, rise), z, material, BlockRole.DECORATION, stage);
            }
        }

        private void gableRoofZ(int x1, int x2, int z1, int z2, int y, Material material, int stage) {
            int width = x2 - x1;
            int half = width / 2;
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
                int rise = Math.min(x - x1, x2 - x);
                block(x, y + Math.min(half, rise), z, material, BlockRole.DECORATION, stage);
            }
        }

        private void chimney(int x, int z, int y1, int y2, Material material, int stage) {
            for (int y = y1; y <= y2; y++) ring(x - 1, x + 1, z - 1, z + 1, y, material, BlockRole.RESIDENT, stage);
        }

        private void tower(int centerX, int centerZ, int radius, int height, Material wall, Material frame, int stage) {
            for (int y = 1; y <= height; y++) ring(centerX - radius, centerX + radius, centerZ - radius,
                    centerZ + radius, y, wall, BlockRole.RESIDENT, stage);
            for (int x : new int[]{centerX - radius, centerX + radius})
                for (int z : new int[]{centerZ - radius, centerZ + radius}) accentPillar(x, z, 1, height, frame, stage);
        }

        private void stall(int centerX, int centerZ, boolean alongX, int stage) {
            int x1 = centerX - (alongX ? 2 : 1), x2 = centerX + (alongX ? 2 : 1);
            int z1 = centerZ - (alongX ? 1 : 2), z2 = centerZ + (alongX ? 1 : 2);
            floor(x1, x2, z1, z2, 0, Material.OAK_PLANKS, stage);
            for (int x : new int[]{x1, x2}) for (int z : new int[]{z1, z2}) pillar(x, z, 1, 2, Material.OAK_LOG, stage);
            flatRoof(x1, x2, z1, z2, 3, (centerX + centerZ) % 2 == 0 ? Material.RED_WOOL : Material.YELLOW_WOOL, stage);
            block(centerX, 1, centerZ, Material.BARREL, BlockRole.DECORATION, stage);
        }

        private void box(int x, int y, int z, int width, int height, int depth, Material material, BlockRole role, int stage) {
            for (int dx = 0; dx < width; dx++) for (int dy = 0; dy < height; dy++) for (int dz = 0; dz < depth; dz++)
                block(x + dx, y + dy, z + dz, material, role, stage);
        }

        private void cylinder(int cx, int cz, int radius, int y, Material material, BlockRole role, int stage) {
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++)
                if (x * x + z * z <= radius * radius) block(cx + x, y, cz + z, material, role, stage);
        }

        private void ringCircle(int cx, int cz, int radius, int y, Material material, BlockRole role, int stage) {
            List<BlockOffset> circle = new ArrayList<>();
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                int distance = x * x + z * z;
                if (distance <= radius * radius && distance >= (radius - 1) * (radius - 1)) circle.add(new BlockOffset(cx + x, y, cz + z));
            }
            for (BlockOffset point : circle) block(point.x(), point.y(), point.z(), material, role, stage);
        }
    }
}
