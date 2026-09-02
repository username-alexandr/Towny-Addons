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

/**
 * Точные проектные модели второй очереди городских зданий.
 *
 * <p>Стабильные ID отделены от источника геометрии. Сейчас используется процедурная
 * архитектура версии 5; позже эти же ID можно связать с файлами .schem без сброса прогресса.</p>
 */
public final class ExpansionBlueprintGenerator {
    public static final List<String> PROJECTS = List.of(
            "infirmary", "fire_station", "tavern_inn", "city_bank", "embassy",
            "museum", "shipyard", "university", "courthouse", "aqueduct",
            "workshop", "watch_fortress", "hunting_lodge", "fishing_harbor",
            "post_station", "observatory", "prison"
    );

    public static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("infirmary", "Лазарет"),
            Map.entry("fire_station", "Пожарная часть"),
            Map.entry("tavern_inn", "Таверна и постоялый двор"),
            Map.entry("city_bank", "Городской банк"),
            Map.entry("embassy", "Посольство"),
            Map.entry("museum", "Музей"),
            Map.entry("shipyard", "Верфь"),
            Map.entry("university", "Университет"),
            Map.entry("courthouse", "Суд"),
            Map.entry("aqueduct", "Акведук"),
            Map.entry("workshop", "Мастерская"),
            Map.entry("watch_fortress", "Сторожевая крепость"),
            Map.entry("hunting_lodge", "Охотничий дом"),
            Map.entry("fishing_harbor", "Рыбацкая гавань"),
            Map.entry("post_station", "Почтовая станция"),
            Map.entry("observatory", "Обсерватория"),
            Map.entry("prison", "Тюрьма")
    );

    public Set<String> supportedProjects() {
        return Set.copyOf(PROJECTS);
    }

    public BlueprintPlan generate(String rawId, int rawLevel) {
        String id = rawId == null ? "" : rawId.toLowerCase(Locale.ROOT);
        if (!PROJECTS.contains(id)) return null;
        int level = Math.max(1, Math.min(5, rawLevel));
        Builder b = new Builder(level);
        switch (id) {
            case "infirmary" -> infirmary(b);
            case "fire_station" -> fireStation(b);
            case "tavern_inn" -> tavernInn(b);
            case "city_bank" -> cityBank(b);
            case "embassy" -> embassy(b);
            case "museum" -> museum(b);
            case "shipyard" -> shipyard(b);
            case "university" -> university(b);
            case "courthouse" -> courthouse(b);
            case "aqueduct" -> aqueduct(b);
            case "workshop" -> workshop(b);
            case "watch_fortress" -> watchFortress(b);
            case "hunting_lodge" -> huntingLodge(b);
            case "fishing_harbor" -> fishingHarbor(b);
            case "post_station" -> postStation(b);
            case "observatory" -> observatory(b);
            case "prison" -> prison(b);
            default -> throw new IllegalStateException("Неизвестная модель " + id);
        }
        return new BlueprintPlan(id, level, stageName(id, level), b.blocks);
    }

    public String stageName(String id, int level) {
        String[] names = switch (id) {
            case "infirmary" -> new String[]{"Приёмный покой", "Палатное крыло", "Аптекарский дом", "Башня лекаря", "Лечебный сад"};
            case "fire_station" -> new String[]{"Дежурный дом", "Пожарное депо", "Водяная башня", "Конюшня команды", "Учебный двор"};
            case "tavern_inn" -> new String[]{"Таверна", "Гостевое крыло", "Кухня и погреб", "Конюшня", "Постоялый двор"};
            case "city_bank" -> new String[]{"Расчётная палата", "Кассовый зал", "Хранилище", "Счётная башня", "Банковская площадь"};
            case "embassy" -> new String[]{"Дом посланника", "Приёмный зал", "Гостевой флигель", "Дипломатическая башня", "Парадный двор"};
            case "museum" -> new String[]{"Выставочный дом", "Западная галерея", "Восточная галерея", "Купольный зал", "Музейный двор"};
            case "shipyard" -> new String[]{"Корабельный сарай", "Стапель", "Кран", "Склад снастей", "Корабельная гавань"};
            case "university" -> new String[]{"Учебный корпус", "Библиотечное крыло", "Алхимическое крыло", "Башня знаний", "Университетский двор"};
            case "courthouse" -> new String[]{"Судебная палата", "Зал заседаний", "Архив суда", "Башня закона", "Площадь правосудия"};
            case "aqueduct" -> new String[]{"Водосборник", "Первая аркада", "Главная аркада", "Напорная башня", "Городской водовод"};
            case "workshop" -> new String[]{"Дом мастера", "Производственный зал", "Склад материалов", "Подъёмный кран", "Ремесленный двор"};
            case "watch_fortress" -> new String[]{"Воротный дом", "Крепостные стены", "Угловые башни", "Дозорная башня", "Укреплённый двор"};
            case "hunting_lodge" -> new String[]{"Охотничья изба", "Трофейный зал", "Псарня", "Наблюдательная вышка", "Стрелковый двор"};
            case "fishing_harbor" -> new String[]{"Дом рыбака", "Рыбный причал", "Рынок улова", "Портовый кран", "Малая гавань"};
            case "post_station" -> new String[]{"Почтовый дом", "Сортировочное крыло", "Конюшня", "Часовая башня", "Станционный двор"};
            case "observatory" -> new String[]{"Дом астронома", "Наблюдательная башня", "Купол", "Большой телескоп", "Звёздный двор"};
            case "prison" -> new String[]{"Караульный корпус", "Камерный блок", "Внешняя стена", "Сторожевые башни", "Тюремный двор"};
            default -> new String[]{"Основание", "Корпус", "Расширение", "Башня", "Завершение"};
        };
        return names[Math.max(1, Math.min(5, level)) - 1];
    }

    private void infirmary(Builder b) {
        b.house(-7, 7, -5, 5, 4, Material.POLISHED_DIORITE, Material.CALCITE,
                Material.STRIPPED_BIRCH_LOG, Material.RED_NETHER_BRICK_STAIRS, Material.BIRCH_DOOR, 1, true);
        b.cross(0, 6, -6, Material.RED_WOOL, 1);
        b.house(8, 15, -4, 5, 3, Material.STONE_BRICKS, Material.WHITE_TERRACOTTA,
                Material.STRIPPED_BIRCH_LOG, Material.BIRCH_STAIRS, Material.BIRCH_DOOR, 2, false);
        b.house(-15, -8, -4, 5, 3, Material.MOSSY_COBBLESTONE, Material.LIME_TERRACOTTA,
                Material.STRIPPED_OAK_LOG, Material.OAK_STAIRS, Material.OAK_DOOR, 3, false);
        b.tower(0, 9, 3, 9, Material.POLISHED_DIORITE, Material.QUARTZ_PILLAR, 4);
        b.door(0, 1, 6, Material.BIRCH_DOOR, BlockFace.SOUTH, Door.Hinge.LEFT, 4);
        b.hipRoof(-4, 4, 5, 13, 10, Material.RED_NETHER_BRICKS, 4);
        b.cross(0, 12, 4, Material.RED_WOOL, 4);
        b.fencedGarden(-14, 14, -11, -7, Material.BIRCH_FENCE, Material.MOSS_BLOCK, 5);
        for (int x = -10; x <= 10; x += 5) b.flowerBed(x, -9, 5);
        b.path(-1, 1, -13, -5, Material.SMOOTH_STONE, 5);
    }

    private void fireStation(Builder b) {
        b.house(-7, 7, -5, 5, 4, Material.STONE_BRICKS, Material.BRICKS,
                Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_STAIRS, Material.DARK_OAK_DOOR, 1, true);
        b.largeGate(-2, 0, 1, -5, Material.DARK_OAK_DOOR, 1);
        b.house(8, 15, -4, 5, 3, Material.COBBLESTONE, Material.BRICKS,
                Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_STAIRS, Material.DARK_OAK_DOOR, 2, false);
        b.tower(-11, 1, 3, 12, Material.BRICKS, Material.POLISHED_ANDESITE, 3);
        b.door(-11, 1, -2, Material.IRON_DOOR, BlockFace.NORTH, Door.Hinge.LEFT, 3);
        b.flatRoof(-15, -7, -3, 5, 13, Material.STONE_BRICKS, 3);
        b.crenellations(-15, -7, -3, 5, 14, Material.BRICK_WALL, 3);
        b.cistern(11, 10, 3, 4);
        b.canopy(-5, 5, 7, 13, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_PLANKS, 4);
        b.fencedYard(-8, 15, 6, 15, Material.DARK_OAK_FENCE, Material.COARSE_DIRT, 5);
        b.path(-2, 2, -12, -5, Material.STONE_BRICKS, 5);
        b.lamp(-7, -8, 5); b.lamp(7, -8, 5);
    }

    private void tavernInn(Builder b) {
        b.house(-7, 7, -5, 6, 5, Material.COBBLESTONE, Material.OAK_PLANKS,
                Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_STAIRS, Material.SPRUCE_DOOR, 1, true);
        b.chimney(5, 3, 1, 9, Material.BRICKS, 1);
        b.house(8, 16, -3, 8, 6, Material.COBBLESTONE, Material.WHITE_TERRACOTTA,
                Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_STAIRS, Material.SPRUCE_DOOR, 2, false);
        b.house(-15, -8, -3, 6, 3, Material.MOSSY_COBBLESTONE, Material.BRICKS,
                Material.STRIPPED_OAK_LOG, Material.DARK_OAK_STAIRS, Material.DARK_OAK_DOOR, 3, false);
        b.chimney(-12, 3, 1, 8, Material.BRICKS, 3);
        b.stable(7, 16, 10, 16, 4);
        b.fencedYard(-15, 5, 8, 17, Material.SPRUCE_FENCE, Material.PODZOL, 5);
        b.canopy(-6, 5, 8, 12, Material.STRIPPED_SPRUCE_LOG, Material.RED_WOOL, 5);
        b.signPost(0, -8, 5);
        b.path(-2, 2, -12, -5, Material.COBBLESTONE, 5);
    }

    private void cityBank(Builder b) {
        b.classicalHall(-8, 8, -4, 7, 5, Material.STONE_BRICKS, Material.SMOOTH_QUARTZ,
                Material.QUARTZ_PILLAR, Material.DEEPSLATE_TILE_STAIRS, 1);
        b.portico(-6, 6, -8, -4, 5, Material.QUARTZ_PILLAR, Material.SMOOTH_QUARTZ, 2);
        b.house(-15, -9, -3, 7, 4, Material.POLISHED_ANDESITE, Material.STONE_BRICKS,
                Material.QUARTZ_PILLAR, Material.DEEPSLATE_TILE_STAIRS, Material.IRON_DOOR, 3, false);
        b.vault(9, 14, -2, 6, 3);
        b.tower(0, 11, 3, 9, Material.STONE_BRICKS, Material.QUARTZ_PILLAR, 4);
        b.flatRoof(-4, 4, 7, 15, 10, Material.DEEPSLATE_TILES, 4);
        b.block(0, 11, 7, Material.GOLD_BLOCK, BlockRole.DECORATION, 4);
        b.plaza(-12, 12, -14, -9, Material.SMOOTH_STONE, 5);
        b.path(-2, 2, -14, -4, Material.POLISHED_ANDESITE, 5);
        b.lamp(-10, -11, 5); b.lamp(10, -11, 5);
    }

    private void embassy(Builder b) {
        b.house(-7, 7, -4, 7, 5, Material.SMOOTH_SANDSTONE, Material.WHITE_TERRACOTTA,
                Material.STRIPPED_BIRCH_LOG, Material.DARK_PRISMARINE_STAIRS, Material.BIRCH_DOOR, 1, true);
        b.portico(-4, 4, -8, -4, 4, Material.QUARTZ_PILLAR, Material.SMOOTH_SANDSTONE, 2);
        b.house(-14, -8, -2, 7, 3, Material.SANDSTONE, Material.LIGHT_BLUE_TERRACOTTA,
                Material.STRIPPED_BIRCH_LOG, Material.DARK_PRISMARINE_STAIRS, Material.BIRCH_DOOR, 3, false);
        b.house(8, 14, -2, 7, 3, Material.SANDSTONE, Material.LIGHT_BLUE_TERRACOTTA,
                Material.STRIPPED_BIRCH_LOG, Material.DARK_PRISMARINE_STAIRS, Material.BIRCH_DOOR, 3, false);
        b.tower(0, 10, 2, 9, Material.CUT_SANDSTONE, Material.QUARTZ_PILLAR, 4);
        b.hipRoof(-3, 3, 7, 13, 10, Material.DARK_PRISMARINE, 4);
        b.flag(-8, -6, Material.BLUE_WOOL, 5); b.flag(8, -6, Material.YELLOW_WOOL, 5);
        b.fencedGarden(-14, 14, -14, -10, Material.BIRCH_FENCE, Material.MOSS_BLOCK, 5);
        b.path(-2, 2, -15, -4, Material.SMOOTH_SANDSTONE, 5);
    }

    private void museum(Builder b) {
        b.classicalHall(-7, 7, -3, 9, 5, Material.STONE_BRICKS, Material.CALCITE,
                Material.QUARTZ_PILLAR, Material.CUT_COPPER_STAIRS, 1);
        b.gallery(-16, -8, -2, 10, Material.POLISHED_ANDESITE, Material.CALCITE, 2);
        b.gallery(8, 16, -2, 10, Material.POLISHED_ANDESITE, Material.CALCITE, 3);
        b.portico(-6, 6, -8, -3, 5, Material.QUARTZ_PILLAR, Material.SMOOTH_QUARTZ, 3);
        b.dome(0, 4, 10, Material.COPPER_BLOCK, Material.GLASS, 4);
        b.domeSupports(0, 4, 10, Material.QUARTZ_PILLAR, 4);
        b.plaza(-14, 14, -15, -9, Material.SMOOTH_STONE, 5);
        b.statue(-8, -11, 5); b.statue(8, -11, 5);
        b.path(-2, 2, -15, -3, Material.POLISHED_DIORITE, 5);
    }

    private void shipyard(Builder b) {
        b.house(-13, -3, -3, 9, 5, Material.COBBLESTONE, Material.SPRUCE_PLANKS,
                Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_STAIRS, Material.SPRUCE_DOOR, 1, true);
        b.slipway(0, 8, -3, 18, 2);
        b.shipFrame(4, 4, 14, 2);
        b.crane(12, 4, 3);
        b.house(10, 17, -2, 8, 3, Material.STONE_BRICKS, Material.DARK_OAK_PLANKS,
                Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_STAIRS, Material.DARK_OAK_DOOR, 4, false);
        b.waterPlane(-16, 18, 10, 22, 0, 5);
        b.pier(-15, -8, 9, 21, 5); b.pier(11, 16, 9, 21, 5);
        b.lamp(-12, 12, 5); b.lamp(14, 12, 5);
    }

    private void university(Builder b) {
        b.house(-8, 8, 0, 10, 5, Material.STONE_BRICKS, Material.BRICKS,
                Material.QUARTZ_PILLAR, Material.DEEPSLATE_TILE_STAIRS, Material.DARK_OAK_DOOR, 1, true);
        b.house(-17, -9, -7, 10, 4, Material.COBBLESTONE, Material.BRICKS,
                Material.QUARTZ_PILLAR, Material.DEEPSLATE_TILE_STAIRS, Material.DARK_OAK_DOOR, 2, false);
        b.house(9, 17, -7, 10, 4, Material.COBBLESTONE, Material.BRICKS,
                Material.QUARTZ_PILLAR, Material.DEEPSLATE_TILE_STAIRS, Material.DARK_OAK_DOOR, 3, false);
        b.tower(0, 14, 3, 12, Material.STONE_BRICKS, Material.QUARTZ_PILLAR, 4);
        b.hipRoof(-4, 4, 10, 18, 13, Material.DEEPSLATE_TILES, 4);
        b.block(0, 13, 10, Material.YELLOW_GLAZED_TERRACOTTA, BlockRole.DECORATION, 4);
        b.fencedGarden(-8, 8, -10, -2, Material.DARK_OAK_FENCE, Material.MOSS_BLOCK, 5);
        b.fountain(0, -6, 5); b.path(-2, 2, -13, 0, Material.STONE_BRICKS, 5);
    }

    private void courthouse(Builder b) {
        b.classicalHall(-9, 9, -2, 10, 6, Material.SMOOTH_STONE, Material.SANDSTONE,
                Material.QUARTZ_PILLAR, Material.STONE_BRICK_STAIRS, 1);
        b.portico(-7, 7, -9, -2, 6, Material.QUARTZ_PILLAR, Material.SMOOTH_SANDSTONE, 2);
        b.house(-16, -10, 1, 10, 4, Material.STONE_BRICKS, Material.SANDSTONE,
                Material.QUARTZ_PILLAR, Material.STONE_BRICK_STAIRS, Material.IRON_DOOR, 3, false);
        b.tower(0, 13, 2, 10, Material.SANDSTONE, Material.QUARTZ_PILLAR, 4);
        b.hipRoof(-3, 3, 10, 16, 11, Material.STONE_BRICKS, 4);
        b.block(0, 12, 11, Material.BELL, BlockRole.DECORATION, 4);
        b.plaza(-14, 14, -16, -10, Material.SMOOTH_STONE, 5);
        b.stairway(-4, 4, -11, -9, Material.QUARTZ_STAIRS, 5);
        b.statue(0, -13, 5);
    }

    private void aqueduct(Builder b) {
        b.reservoir(-16, 0, 5, 1);
        b.aqueductSpan(-12, -3, 5, 12, 2);
        b.aqueductSpan(-2, 7, 5, 12, 3);
        b.aqueductSpan(8, 17, 5, 12, 3);
        b.tower(21, 5, 3, 14, Material.STONE_BRICKS, Material.POLISHED_ANDESITE, 4);
        b.flatRoof(17, 25, 1, 9, 15, Material.SMOOTH_STONE, 4);
        b.waterChannel(-17, 24, 4, 13, 5);
        b.path(-18, 25, -1, 0, Material.COBBLESTONE, 5);
        b.lamp(-10, -1, 5); b.lamp(4, -1, 5); b.lamp(18, -1, 5);
    }

    private void workshop(Builder b) {
        b.house(-7, 7, -5, 6, 4, Material.COBBLESTONE, Material.BRICKS,
                Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_STAIRS, Material.DARK_OAK_DOOR, 1, true);
        b.chimney(5, 3, 1, 10, Material.BRICKS, 1);
        b.house(8, 16, -3, 7, 4, Material.STONE_BRICKS, Material.DARK_OAK_PLANKS,
                Material.STRIPPED_DARK_OAK_LOG, Material.DEEPSLATE_TILE_STAIRS, Material.DARK_OAK_DOOR, 2, false);
        b.house(-15, -8, -3, 7, 3, Material.COBBLESTONE, Material.SPRUCE_PLANKS,
                Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_STAIRS, Material.SPRUCE_DOOR, 3, false);
        b.crane(12, 11, 4);
        b.fencedYard(-15, 7, 8, 16, Material.DARK_OAK_FENCE, Material.COARSE_DIRT, 5);
        b.canopy(-5, 5, 9, 14, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_PLANKS, 5);
        b.anvilRow(-3, 3, 11, 5);
    }

    private void watchFortress(Builder b) {
        b.gatehouse(-6, 6, -5, 5, 7, 1);
        b.fortressWall(-16, 16, -10, 14, 5, 2);
        for (int x : new int[]{-14, 14}) for (int z : new int[]{-8, 12}) {
            b.roundTower(x, z, 3, 9, Material.DEEPSLATE_BRICKS, 3);
        }
        b.tower(0, 10, 3, 14, Material.STONE_BRICKS, Material.POLISHED_BASALT, 4);
        b.hipRoof(-4, 4, 6, 14, 15, Material.DEEPSLATE_TILES, 4);
        b.fortressYard(-11, 11, -3, 10, 5);
        b.path(-2, 2, -15, 12, Material.STONE_BRICKS, 5);
    }

    private void huntingLodge(Builder b) {
        b.house(-7, 7, -5, 6, 4, Material.MOSSY_COBBLESTONE, Material.SPRUCE_PLANKS,
                Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_STAIRS, Material.SPRUCE_DOOR, 1, true);
        b.chimney(5, 3, 1, 9, Material.COBBLESTONE, 1);
        b.house(8, 15, -3, 6, 3, Material.COBBLESTONE, Material.DARK_OAK_PLANKS,
                Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_STAIRS, Material.DARK_OAK_DOOR, 2, false);
        b.kennel(-14, -9, -2, 5, 3);
        b.watchPlatform(11, 12, 4);
        b.fencedYard(-14, 6, 8, 15, Material.SPRUCE_FENCE, Material.PODZOL, 5);
        b.archeryRange(-11, 2, 10, 5);
        b.path(-1, 1, -11, -5, Material.COARSE_DIRT, 5);
    }

    private void fishingHarbor(Builder b) {
        b.house(-13, -4, -4, 6, 4, Material.COBBLESTONE, Material.SPRUCE_PLANKS,
                Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_STAIRS, Material.SPRUCE_DOOR, 1, false);
        b.pier(-2, 3, -1, 19, 2);
        b.canopy(5, 14, -3, 5, Material.STRIPPED_OAK_LOG, Material.BLUE_WOOL, 3);
        b.crane(10, 10, 4);
        b.waterPlane(-16, 17, 7, 23, 0, 5);
        b.pier(-14, -8, 6, 20, 5); b.pier(7, 14, 6, 17, 5);
        b.smallBoat(-1, 15, 5); b.smallBoat(11, 18, 5);
        b.lamp(-11, 9, 5); b.lamp(10, 8, 5);
    }

    private void postStation(Builder b) {
        b.house(-7, 7, -5, 6, 4, Material.COBBLESTONE, Material.BRICKS,
                Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_STAIRS, Material.DARK_OAK_DOOR, 1, true);
        b.house(8, 15, -3, 6, 3, Material.COBBLESTONE, Material.OAK_PLANKS,
                Material.STRIPPED_OAK_LOG, Material.OAK_STAIRS, Material.OAK_DOOR, 2, false);
        b.stable(-15, -8, -3, 8, 3);
        b.tower(0, 9, 2, 10, Material.BRICKS, Material.STRIPPED_DARK_OAK_LOG, 4);
        b.hipRoof(-3, 3, 6, 12, 11, Material.DARK_OAK_PLANKS, 4);
        b.block(0, 9, 7, Material.YELLOW_GLAZED_TERRACOTTA, BlockRole.DECORATION, 4);
        b.fencedYard(-15, 15, 8, 15, Material.OAK_FENCE, Material.PACKED_MUD, 5);
        b.canopy(-5, 5, 8, 12, Material.STRIPPED_DARK_OAK_LOG, Material.RED_WOOL, 5);
        b.signPost(0, -8, 5);
    }

    private void observatory(Builder b) {
        b.house(-7, 7, -5, 5, 4, Material.STONE_BRICKS, Material.POLISHED_DIORITE,
                Material.QUARTZ_PILLAR, Material.DEEPSLATE_TILE_STAIRS, Material.IRON_DOOR, 1, false);
        b.roundTower(0, 3, 5, 11, Material.POLISHED_ANDESITE, 2);
        b.dome(0, 3, 12, Material.SMOOTH_QUARTZ, Material.BLUE_STAINED_GLASS, 3);
        b.domeSupports(0, 3, 12, Material.QUARTZ_PILLAR, 3);
        b.telescope(0, 14, 3, 4);
        b.fencedGarden(-13, 13, -12, -8, Material.IRON_BARS, Material.DEEPSLATE_TILES, 5);
        for (int x = -9; x <= 9; x += 6) b.starMarker(x, -10, 5);
        b.path(-1, 1, -13, -5, Material.POLISHED_DIORITE, 5);
    }

    private void prison(Builder b) {
        b.house(-7, 7, -8, -1, 4, Material.STONE_BRICKS, Material.POLISHED_ANDESITE,
                Material.POLISHED_BASALT, Material.DEEPSLATE_TILE_STAIRS, Material.IRON_DOOR, 1, true);
        b.cellBlock(-9, 9, 1, 13, 5, 2);
        b.prisonWall(-16, 16, -13, 18, 6, 3);
        for (int x : new int[]{-14, 14}) for (int z : new int[]{-11, 16}) {
            b.roundTower(x, z, 2, 10, Material.DEEPSLATE_BRICKS, 4);
        }
        b.prisonYard(-12, 12, -5, 15, 5);
        b.path(-2, 2, -17, -8, Material.STONE_BRICKS, 5);
    }

    private static final class Builder {
        private final int target;
        private final Map<BlockOffset, BlueprintBlock> blocks = new LinkedHashMap<>();

        private Builder(int target) { this.target = target; }
        private boolean stage(int stage) { return target >= stage; }

        private void block(int x, int y, int z, Material material, BlockRole role, int stage) {
            if (!stage(stage) || material == Material.AIR) return;
            blocks.putIfAbsent(new BlockOffset(x, y, z), new BlueprintBlock(material, role, stage));
        }

        private void replace(int x, int y, int z, Material material, BlockRole role, int stage) {
            if (!stage(stage) || material == Material.AIR) return;
            BlockOffset key = new BlockOffset(x, y, z);
            BlueprintBlock old = blocks.get(key);
            if (old == null || old.stage() == stage) blocks.put(key, new BlueprintBlock(material, role, stage));
        }

        private void axis(int x, int y, int z, Material material, BlockRole role, int stage, Axis axis) {
            if (!stage(stage)) return;
            blocks.putIfAbsent(new BlockOffset(x, y, z), new BlueprintBlock(material, role, stage).withAxis(axis));
        }

        private void stair(int x, int y, int z, Material material, BlockFace face, int stage) {
            if (!stage(stage)) return;
            blocks.putIfAbsent(new BlockOffset(x, y, z), new BlueprintBlock(material, BlockRole.DECORATION, stage)
                    .asStair(face, Bisected.Half.BOTTOM));
        }

        private void door(int x, int y, int z, Material material, BlockFace face, Door.Hinge hinge, int stage) {
            if (!stage(stage)) return;
            blocks.putIfAbsent(new BlockOffset(x, y, z), new BlueprintBlock(material, BlockRole.DECORATION, stage)
                    .asDoor(face, Bisected.Half.BOTTOM, hinge));
            blocks.putIfAbsent(new BlockOffset(x, y + 1, z), new BlueprintBlock(material, BlockRole.DECORATION, stage)
                    .asDoor(face, Bisected.Half.TOP, hinge));
        }

        private void floor(int x1, int x2, int z1, int z2, int y, Material material, int stage) {
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++)
                block(x, y, z, material, BlockRole.RESIDENT, stage);
        }

        private void path(int x1, int x2, int z1, int z2, Material material, int stage) {
            floor(Math.min(x1, x2), Math.max(x1, x2), Math.min(z1, z2), Math.max(z1, z2), 0, material, stage);
        }

        private void ring(int x1, int x2, int z1, int z2, int y, Material material, BlockRole role, int stage) {
            for (int x = x1; x <= x2; x++) { block(x, y, z1, material, role, stage); block(x, y, z2, material, role, stage); }
            for (int z = z1 + 1; z < z2; z++) { block(x1, y, z, material, role, stage); block(x2, y, z, material, role, stage); }
        }

        private void pillar(int x, int z, int y1, int y2, Material material, int stage) {
            for (int y = y1; y <= y2; y++) axis(x, y, z, material, BlockRole.RESIDENT, stage, Axis.Y);
        }

        private void house(int x1, int x2, int z1, int z2, int wallTop, Material foundation,
                           Material wall, Material frame, Material roof, Material door,
                           int stage, boolean doubleDoor) {
            floor(x1, x2, z1, z2, 0, foundation, stage);
            int center = (x1 + x2) / 2;
            int first = doubleDoor ? center - 1 : center;
            int second = center;
            for (int y = 1; y <= wallTop; y++) {
                for (int x = x1; x <= x2; x++) {
                    boolean entrance = y <= 2 && (x == first || (doubleDoor && x == second));
                    if (!entrance) block(x, y, z1, wall, BlockRole.RESIDENT, stage);
                    block(x, y, z2, wall, BlockRole.RESIDENT, stage);
                }
                for (int z = z1 + 1; z < z2; z++) {
                    block(x1, y, z, wall, BlockRole.RESIDENT, stage);
                    block(x2, y, z, wall, BlockRole.RESIDENT, stage);
                }
            }
            for (int x : new int[]{x1, x2}) for (int z : new int[]{z1, z2}) pillar(x, z, 1, wallTop, frame, stage);
            window(x1 + 2, Math.min(2, wallTop), z1, stage);
            window(x2 - 2, Math.min(2, wallTop), z1, stage);
            window(x1 + 2, Math.min(2, wallTop), z2, stage);
            window(x2 - 2, Math.min(2, wallTop), z2, stage);
            window(x1, Math.min(2, wallTop), (z1 + z2) / 2, stage);
            window(x2, Math.min(2, wallTop), (z1 + z2) / 2, stage);
            if (wallTop >= 5) {
                window(x1 + 2, 4, z1, stage); window(x2 - 2, 4, z1, stage);
                window(x1 + 2, 4, z2, stage); window(x2 - 2, 4, z2, stage);
            }
            door(first, 1, z1, door, BlockFace.NORTH, Door.Hinge.LEFT, stage);
            if (doubleDoor) door(second, 1, z1, door, BlockFace.NORTH, Door.Hinge.RIGHT, stage);
            for (int x = first; x <= second; x++) block(x, 0, z1 - 1, foundation, BlockRole.RESIDENT, stage);
            gableRoof(x1, x2, z1, z2, wallTop + 1, roof, stage);
            gableWalls(x1, x2, z1, z2, wallTop, wall, stage);
            replace((x1 + x2) / 2, wallTop + 2, z1, Material.GLASS_PANE,
                    BlockRole.DECORATION, stage);
            replace((x1 + x2) / 2, wallTop + 2, z2, Material.GLASS_PANE,
                    BlockRole.DECORATION, stage);
        }

        private void window(int x, int y, int z, int stage) {
            replace(x, y, z, Material.GLASS_PANE, BlockRole.DECORATION, stage);
            if (y >= 3) replace(x, y - 1, z, Material.GLASS_PANE, BlockRole.DECORATION, stage);
        }

        private void gableRoof(int x1, int x2, int z1, int z2, int y, Material roof, int stage) {
            int left = x1 - 1, right = x2 + 1, layer = 0;
            while (left + layer < right - layer) {
                int roofY = y + layer;
                for (int z = z1 - 1; z <= z2 + 1; z++) {
                    stair(left + layer, roofY, z, roof, BlockFace.EAST, stage);
                    stair(right - layer, roofY, z, roof, BlockFace.WEST, stage);
                }
                layer++;
            }
            int ridge = (left + right) / 2;
            Material ridgeMaterial = roofBlock(roof);
            for (int z = z1; z <= z2; z++) axis(ridge, y + layer - 1, z, ridgeMaterial, BlockRole.DECORATION, stage, Axis.Z);
        }

        /** Заполняет оба треугольных фронтона под скатами, не затрагивая свес крыши. */
        private void gableWalls(int x1, int x2, int z1, int z2, int wallTop,
                                Material wall, int stage) {
            int leftRoofEdge = x1 - 1;
            int rightRoofEdge = x2 + 1;
            for (int x = x1; x <= x2; x++) {
                int rise = Math.min(x - leftRoofEdge, rightRoofEdge - x);
                for (int y = wallTop + 1; y <= wallTop + rise; y++) {
                    block(x, y, z1, wall, BlockRole.RESIDENT, stage);
                    block(x, y, z2, wall, BlockRole.RESIDENT, stage);
                }
            }
        }

        private Material roofBlock(Material stair) {
            return switch (stair) {
                case OAK_STAIRS -> Material.OAK_PLANKS;
                case BIRCH_STAIRS -> Material.BIRCH_PLANKS;
                case SPRUCE_STAIRS -> Material.SPRUCE_PLANKS;
                case DARK_OAK_STAIRS -> Material.DARK_OAK_PLANKS;
                case BRICK_STAIRS -> Material.BRICKS;
                case STONE_BRICK_STAIRS -> Material.STONE_BRICKS;
                case DEEPSLATE_TILE_STAIRS -> Material.DEEPSLATE_TILES;
                case RED_NETHER_BRICK_STAIRS -> Material.RED_NETHER_BRICKS;
                case DARK_PRISMARINE_STAIRS -> Material.DARK_PRISMARINE;
                case CUT_COPPER_STAIRS -> Material.COPPER_BLOCK;
                default -> Material.STONE_BRICKS;
            };
        }

        private void flatRoof(int x1, int x2, int z1, int z2, int y, Material material, int stage) {
            floor(x1, x2, z1, z2, y, material, stage);
        }

        private void hipRoof(int x1, int x2, int z1, int z2, int y, Material material, int stage) {
            int layer = 0;
            while (x1 + layer <= x2 - layer && z1 + layer <= z2 - layer) {
                ring(x1 + layer, x2 - layer, z1 + layer, z2 - layer, y + layer,
                        material, BlockRole.DECORATION, stage);
                layer++;
            }
        }

        private void tower(int cx, int cz, int radius, int height, Material wall, Material frame, int stage) {
            floor(cx - radius, cx + radius, cz - radius, cz + radius, 0, wall, stage);
            for (int y = 1; y <= height; y++) ring(cx - radius, cx + radius, cz - radius, cz + radius, y, wall, BlockRole.RESIDENT, stage);
            for (int x : new int[]{cx - radius, cx + radius}) for (int z : new int[]{cz - radius, cz + radius}) pillar(x, z, 1, height, frame, stage);
            for (int y = 3; y < height; y += 3) {
                replace(cx, y, cz - radius, Material.GLASS_PANE, BlockRole.DECORATION, stage);
                replace(cx, y, cz + radius, Material.GLASS_PANE, BlockRole.DECORATION, stage);
            }
        }

        private void classicalHall(int x1, int x2, int z1, int z2, int height, Material foundation,
                                   Material wall, Material frame, Material roof, int stage) {
            house(x1, x2, z1, z2, height, foundation, wall, frame, roof, Material.IRON_DOOR, stage, true);
        }

        private void portico(int x1, int x2, int z1, int z2, int height, Material columns, Material roof, int stage) {
            path(x1, x2, z1, z2, Material.SMOOTH_STONE, stage);
            for (int x = x1; x <= x2; x += 3) pillar(x, z1, 1, height, columns, stage);
            pillar(x2, z1, 1, height, columns, stage);
            flatRoof(x1 - 1, x2 + 1, z1 - 1, z2, height + 1, roof, stage);
            stairway(Math.max(x1, -4), Math.min(x2, 4), z1 - 2, z1, Material.QUARTZ_STAIRS, stage);
        }

        private void stairway(int x1, int x2, int z1, int z2, Material material, int stage) {
            int y = 0;
            for (int z = z1; z <= z2; z++) {
                for (int x = x1; x <= x2; x++) stair(x, y, z, material, BlockFace.SOUTH, stage);
                if ((z - z1) % 2 == 1) y++;
            }
        }

        private void gallery(int x1, int x2, int z1, int z2, Material foundation, Material wall, int stage) {
            house(x1, x2, z1, z2, 4, foundation, wall, Material.QUARTZ_PILLAR,
                    Material.CUT_COPPER_STAIRS, Material.BIRCH_DOOR, stage, false);
        }

        private void dome(int cx, int cz, int baseY, Material shell, Material glass, int stage) {
            int[] radii = {5, 5, 4, 4, 3, 2, 1};
            for (int dy = 0; dy < radii.length; dy++) {
                int r = radii[dy];
                for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                    int d = x * x + z * z;
                    if (d <= r * r && d >= (r - 1) * (r - 1)) {
                        Material material = (x == 0 || z == 0) && dy > 0 ? glass : shell;
                        block(cx + x, baseY + dy, cz + z, material, BlockRole.DECORATION, stage);
                    }
                }
            }
            block(cx, baseY + radii.length, cz, Material.LIGHTNING_ROD, BlockRole.DECORATION, stage);
        }

        private void domeSupports(int cx, int cz, int baseY, Material material, int stage) {
            for (int x : new int[]{cx - 3, cx + 3}) {
                for (int z : new int[]{cz - 3, cz + 3}) {
                    pillar(x, z, baseY, baseY + 2, material, stage);
                }
            }
        }

        private void cross(int cx, int cy, int z, Material material, int stage) {
            for (int x = cx - 2; x <= cx + 2; x++) replace(x, cy, z, material, BlockRole.DECORATION, stage);
            for (int y = cy - 2; y <= cy + 2; y++) replace(cx, y, z, material, BlockRole.DECORATION, stage);
        }

        private void largeGate(int x1, int x2, int y, int z, Material material, int stage) {
            for (int x = x1; x <= x2; x++) door(x, y, z, material, BlockFace.NORTH,
                    x == x1 ? Door.Hinge.LEFT : Door.Hinge.RIGHT, stage);
        }

        private void chimney(int x, int z, int y1, int y2, Material material, int stage) {
            for (int y = y1; y <= y2; y++) block(x, y, z, material, BlockRole.RESIDENT, stage);
            block(x, y2 + 1, z, Material.CAMPFIRE, BlockRole.DECORATION, stage);
        }

        private void canopy(int x1, int x2, int z1, int z2, Material frame, Material roof, int stage) {
            floor(x1, x2, z1, z2, 0, Material.COBBLESTONE, stage);
            for (int x : new int[]{x1, x2}) for (int z : new int[]{z1, z2}) pillar(x, z, 1, 3, frame, stage);
            flatRoof(x1 - 1, x2 + 1, z1 - 1, z2 + 1, 4, roof, stage);
        }

        private void fencedYard(int x1, int x2, int z1, int z2, Material fence, Material ground, int stage) {
            floor(x1 + 1, x2 - 1, z1 + 1, z2 - 1, 0, ground, stage);
            ring(x1, x2, z1, z2, 1, fence, BlockRole.DECORATION, stage);
            int center = (x1 + x2) / 2;
            replace(center, 1, z1, Material.OAK_FENCE_GATE, BlockRole.DECORATION, stage);
        }

        private void fencedGarden(int x1, int x2, int z1, int z2, Material fence, Material ground, int stage) {
            fencedYard(x1, x2, z1, z2, fence, ground, stage);
            for (int x = x1 + 2; x <= x2 - 2; x += 4) flowerBed(x, (z1 + z2) / 2, stage);
        }

        private void flowerBed(int x, int z, int stage) {
            block(x, 1, z, Material.FLOWERING_AZALEA, BlockRole.DECORATION, stage);
            block(x + 1, 1, z, Material.AZALEA, BlockRole.DECORATION, stage);
        }

        private void cistern(int cx, int cz, int radius, int stage) {
            for (int y = 1; y <= 3; y++) ring(cx - radius, cx + radius, cz - radius, cz + radius, y,
                    Material.STONE_BRICKS, BlockRole.RESIDENT, stage);
            floor(cx - radius + 1, cx + radius - 1, cz - radius + 1, cz + radius - 1, 1, Material.WATER, stage);
            pillar(cx - radius, cz - radius, 1, 6, Material.STRIPPED_OAK_LOG, stage);
            pillar(cx + radius, cz - radius, 1, 6, Material.STRIPPED_OAK_LOG, stage);
        }

        private void stable(int x1, int x2, int z1, int z2, int stage) {
            canopy(x1, x2, z1, z2, Material.STRIPPED_OAK_LOG, Material.OAK_PLANKS, stage);
            for (int x = x1 + 2; x < x2; x += 3) {
                pillar(x, z2, 1, 2, Material.OAK_FENCE, stage);
                block(x, 1, (z1 + z2) / 2, Material.HAY_BLOCK, BlockRole.DECORATION, stage);
            }
        }

        private void signPost(int x, int z, int stage) {
            pillar(x, z, 1, 3, Material.OAK_FENCE, stage);
            block(x, 4, z, Material.OAK_SIGN, BlockRole.DECORATION, stage);
            block(x, 3, z + 1, Material.LANTERN, BlockRole.DECORATION, stage);
        }

        private void vault(int x1, int x2, int z1, int z2, int stage) {
            floor(x1, x2, z1, z2, 0, Material.DEEPSLATE_BRICKS, stage);
            for (int y = 1; y <= 4; y++) ring(x1, x2, z1, z2, y, Material.REINFORCED_DEEPSLATE, BlockRole.RESIDENT, stage);
            door((x1 + x2) / 2, 1, z1, Material.IRON_DOOR, BlockFace.NORTH, Door.Hinge.LEFT, stage);
            flatRoof(x1, x2, z1, z2, 5, Material.DEEPSLATE_TILES, stage);
            block((x1 + x2) / 2, 1, (z1 + z2) / 2, Material.GOLD_BLOCK, BlockRole.DECORATION, stage);
        }

        private void plaza(int x1, int x2, int z1, int z2, Material material, int stage) {
            floor(x1, x2, z1, z2, 0, material, stage);
        }

        private void flag(int x, int z, Material color, int stage) {
            pillar(x, z, 1, 8, Material.OAK_FENCE, stage);
            for (int y = 6; y <= 8; y++) for (int dx = 1; dx <= 3; dx++) block(x + dx, y, z, color, BlockRole.DECORATION, stage);
        }

        private void statue(int x, int z, int stage) {
            floor(x - 1, x + 1, z - 1, z + 1, 0, Material.POLISHED_ANDESITE, stage);
            pillar(x, z, 1, 3, Material.QUARTZ_PILLAR, stage);
            block(x, 4, z, Material.CARVED_PUMPKIN, BlockRole.DECORATION, stage);
        }

        private void slipway(int x1, int x2, int z1, int z2, int stage) {
            for (int z = z1; z <= z2; z += 2) {
                for (int x = x1; x <= x2; x++) block(x, 0, z, Material.OAK_PLANKS, BlockRole.RESIDENT, stage);
            }
            for (int x : new int[]{x1, x2}) for (int z = z1; z <= z2; z++) block(x, 1, z, Material.SPRUCE_FENCE, BlockRole.DECORATION, stage);
        }

        private void shipFrame(int cx, int z1, int z2, int stage) {
            for (int z = z1; z <= z2; z++) {
                int half = Math.max(1, Math.min(4, (z - z1 + 2) / 2));
                block(cx - half, 1, z, Material.SPRUCE_PLANKS, BlockRole.RESIDENT, stage);
                block(cx + half, 1, z, Material.SPRUCE_PLANKS, BlockRole.RESIDENT, stage);
                if ((z - z1) % 3 == 0) for (int x = cx - half; x <= cx + half; x++) block(x, 0, z, Material.OAK_LOG, BlockRole.RESIDENT, stage);
            }
            pillar(cx, (z1 + z2) / 2, 1, 8, Material.STRIPPED_SPRUCE_LOG, stage);
        }

        private void crane(int cx, int cz, int stage) {
            for (int x : new int[]{cx - 2, cx + 2}) pillar(x, cz, 1, 8, Material.STRIPPED_DARK_OAK_LOG, stage);
            for (int x = cx - 3; x <= cx + 7; x++) axis(x, 9, cz, Material.DARK_OAK_LOG, BlockRole.RESIDENT, stage, Axis.X);
            for (int y = 5; y <= 8; y++) block(cx + 6, y, cz, Material.IRON_CHAIN, BlockRole.DECORATION, stage);
            block(cx + 6, 4, cz, Material.IRON_BLOCK, BlockRole.DECORATION, stage);
        }

        private void waterPlane(int x1, int x2, int z1, int z2, int y, int stage) {
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) block(x, y, z, Material.WATER, BlockRole.DECORATION, stage);
        }

        private void pier(int x1, int x2, int z1, int z2, int stage) {
            floor(x1, x2, z1, z2, 1, Material.SPRUCE_PLANKS, stage);
            for (int x : new int[]{x1, x2}) for (int z = z1; z <= z2; z += 4) pillar(x, z, 0, 2, Material.STRIPPED_SPRUCE_LOG, stage);
        }

        private void fountain(int cx, int cz, int stage) {
            ring(cx - 3, cx + 3, cz - 3, cz + 3, 0, Material.STONE_BRICKS, BlockRole.RESIDENT, stage);
            floor(cx - 2, cx + 2, cz - 2, cz + 2, 0, Material.WATER, stage);
            pillar(cx, cz, 1, 3, Material.QUARTZ_PILLAR, stage);
            block(cx, 4, cz, Material.WATER, BlockRole.DECORATION, stage);
        }

        private void reservoir(int cx, int cz, int radius, int stage) {
            for (int y = 1; y <= 12; y++) ring(cx - radius, cx + radius, cz - radius, cz + radius, y,
                    Material.STONE_BRICKS, BlockRole.RESIDENT, stage);
            floor(cx - radius + 1, cx + radius - 1, cz - radius + 1, cz + radius - 1, 12, Material.WATER, stage);
            ring(cx - radius - 1, cx + radius + 1, cz - radius - 1, cz + radius + 1, 13,
                    Material.SMOOTH_STONE, BlockRole.DECORATION, stage);
        }

        private void aqueductSpan(int x1, int x2, int cz, int top, int stage) {
            for (int x = x1; x <= x2; x++) {
                int local = Math.floorMod(x - x1, 6);
                for (int y = 1; y <= top; y++) {
                    boolean pier = local <= 1 || local >= 5;
                    boolean lintel = y >= top - 1;
                    if (pier || lintel) block(x, y, cz, Material.STONE_BRICKS, BlockRole.RESIDENT, stage);
                }
            }
            for (int x = x1; x <= x2; x++) block(x, top + 1, cz, Material.SMOOTH_STONE, BlockRole.DECORATION, stage);
        }

        private void waterChannel(int x1, int x2, int z, int y, int stage) {
            for (int x = x1; x <= x2; x++) {
                block(x, y, z - 1, Material.STONE_BRICKS, BlockRole.RESIDENT, stage);
                block(x, y, z + 1, Material.STONE_BRICKS, BlockRole.RESIDENT, stage);
                block(x, y, z, Material.WATER, BlockRole.DECORATION, stage);
            }
        }

        private void lamp(int x, int z, int stage) {
            pillar(x, z, 1, 3, Material.IRON_BARS, stage);
            block(x, 4, z, Material.LANTERN, BlockRole.DECORATION, stage);
        }

        private void anvilRow(int x1, int x2, int z, int stage) {
            for (int x = x1; x <= x2; x += 3) block(x, 1, z, Material.ANVIL, BlockRole.DECORATION, stage);
        }

        private void gatehouse(int x1, int x2, int z1, int z2, int height, int stage) {
            floor(x1, x2, z1, z2, 0, Material.DEEPSLATE_BRICKS, stage);
            for (int y = 1; y <= height; y++) {
                for (int x = x1; x <= x2; x++) {
                    boolean gate = x >= -2 && x <= 2 && y <= 4;
                    if (!gate) block(x, y, z1, Material.STONE_BRICKS, BlockRole.RESIDENT, stage);
                    block(x, y, z2, Material.STONE_BRICKS, BlockRole.RESIDENT, stage);
                }
                for (int z = z1 + 1; z < z2; z++) {
                    block(x1, y, z, Material.STONE_BRICKS, BlockRole.RESIDENT, stage);
                    block(x2, y, z, Material.STONE_BRICKS, BlockRole.RESIDENT, stage);
                }
            }
            for (int x = -2; x <= 2; x++) block(x, 5, z1, Material.DEEPSLATE_BRICKS, BlockRole.DECORATION, stage);
            door(-1, 1, z1, Material.IRON_DOOR, BlockFace.NORTH, Door.Hinge.LEFT, stage);
            door(0, 1, z1, Material.IRON_DOOR, BlockFace.NORTH, Door.Hinge.RIGHT, stage);
            flatRoof(x1, x2, z1, z2, height + 1, Material.DEEPSLATE_TILES, stage);
            crenellations(x1, x2, z1, z2, height + 2, Material.DEEPSLATE_BRICK_WALL, stage);
        }

        private void fortressWall(int x1, int x2, int z1, int z2, int height, int stage) {
            for (int y = 1; y <= height; y++) ring(x1, x2, z1, z2, y, Material.STONE_BRICKS, BlockRole.RESIDENT, stage);
            for (int x = -2; x <= 2; x++) for (int y = 1; y <= 4; y++) replace(x, y, z1, Material.IRON_BARS, BlockRole.DECORATION, stage);
            crenellations(x1, x2, z1, z2, height + 1, Material.STONE_BRICK_WALL, stage);
        }

        private void crenellations(int x1, int x2, int z1, int z2, int y, Material material, int stage) {
            for (int x = x1; x <= x2; x += 2) { block(x, y, z1, material, BlockRole.DECORATION, stage); block(x, y, z2, material, BlockRole.DECORATION, stage); }
            for (int z = z1; z <= z2; z += 2) { block(x1, y, z, material, BlockRole.DECORATION, stage); block(x2, y, z, material, BlockRole.DECORATION, stage); }
        }

        private void roundTower(int cx, int cz, int radius, int height, Material wall, int stage) {
            for (int y = 0; y <= height; y++) {
                for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                    int d = x * x + z * z;
                    if (d <= radius * radius && d >= (radius - 1) * (radius - 1)) block(cx + x, y, cz + z, wall, BlockRole.RESIDENT, stage);
                }
            }
            for (int a = 0; a < 8; a++) {
                double angle = a * Math.PI / 4.0;
                block(cx + (int) Math.round(Math.cos(angle) * radius), height + 1,
                        cz + (int) Math.round(Math.sin(angle) * radius), Material.STONE_BRICK_WALL, BlockRole.DECORATION, stage);
            }
        }

        private void fortressYard(int x1, int x2, int z1, int z2, int stage) {
            floor(x1, x2, z1, z2, 0, Material.COBBLESTONE, stage);
            canopy(x1, x1 + 5, z2 - 4, z2, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_PLANKS, stage);
            canopy(x2 - 5, x2, z2 - 4, z2, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_PLANKS, stage);
        }

        private void kennel(int x1, int x2, int z1, int z2, int stage) {
            fencedYard(x1, x2, z1, z2, Material.SPRUCE_FENCE, Material.PODZOL, stage);
            canopy(x1, x2, z2 - 2, z2, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_PLANKS, stage);
        }

        private void watchPlatform(int cx, int cz, int stage) {
            for (int x : new int[]{cx - 2, cx + 2}) for (int z : new int[]{cz - 2, cz + 2}) pillar(x, z, 1, 7, Material.STRIPPED_SPRUCE_LOG, stage);
            floor(cx - 3, cx + 3, cz - 3, cz + 3, 8, Material.SPRUCE_PLANKS, stage);
            ring(cx - 3, cx + 3, cz - 3, cz + 3, 9, Material.SPRUCE_FENCE, BlockRole.DECORATION, stage);
            hipRoof(cx - 4, cx + 4, cz - 4, cz + 4, 10, Material.DARK_OAK_PLANKS, stage);
        }

        private void archeryRange(int x1, int x2, int z, int stage) {
            for (int x = x1; x <= x2; x += 4) {
                block(x, 1, z, Material.HAY_BLOCK, BlockRole.RESIDENT, stage);
                block(x, 2, z, Material.TARGET, BlockRole.DECORATION, stage);
            }
        }

        private void smallBoat(int cx, int cz, int stage) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                int half = (Math.abs(z - cz) == 3) ? 0 : (Math.abs(z - cz) >= 2 ? 1 : 2);
                for (int x = cx - half; x <= cx + half; x++) block(x, 1, z, Material.SPRUCE_PLANKS, BlockRole.DECORATION, stage);
            }
            pillar(cx, cz, 2, 6, Material.OAK_FENCE, stage);
            for (int y = 3; y <= 5; y++) for (int x = cx + 1; x <= cx + 3; x++) block(x, y, cz, Material.WHITE_WOOL, BlockRole.DECORATION, stage);
        }

        private void telescope(int cx, int y, int cz, int stage) {
            pillar(cx, cz, y - 3, y - 1, Material.IRON_BLOCK, stage);
            for (int i = 0; i < 7; i++) {
                block(cx + i, y + i / 3, cz, i == 6 ? Material.AMETHYST_BLOCK : Material.COPPER_BLOCK,
                        BlockRole.DECORATION, stage);
            }
        }

        private void starMarker(int x, int z, int stage) {
            block(x, 1, z, Material.AMETHYST_BLOCK, BlockRole.DECORATION, stage);
            block(x, 2, z, Material.END_ROD, BlockRole.DECORATION, stage);
        }

        private void cellBlock(int x1, int x2, int z1, int z2, int height, int stage) {
            floor(x1, x2, z1, z2, 0, Material.DEEPSLATE_BRICKS, stage);
            for (int y = 1; y <= height; y++) ring(x1, x2, z1, z2, y, Material.STONE_BRICKS, BlockRole.RESIDENT, stage);
            flatRoof(x1, x2, z1, z2, height + 1, Material.DEEPSLATE_TILES, stage);
            for (int x = x1 + 2; x < x2; x += 3) {
                replace(x, 2, z1, Material.IRON_BARS, BlockRole.DECORATION, stage);
                replace(x, 2, z2, Material.IRON_BARS, BlockRole.DECORATION, stage);
            }
            door(0, 1, z1, Material.IRON_DOOR, BlockFace.NORTH, Door.Hinge.LEFT, stage);
        }

        private void prisonWall(int x1, int x2, int z1, int z2, int height, int stage) {
            for (int y = 1; y <= height; y++) ring(x1, x2, z1, z2, y, Material.STONE_BRICKS, BlockRole.RESIDENT, stage);
            for (int x = -2; x <= 2; x++) for (int y = 1; y <= 4; y++) replace(x, y, z1, Material.IRON_BARS, BlockRole.DECORATION, stage);
            crenellations(x1, x2, z1, z2, height + 1, Material.IRON_BARS, stage);
        }

        private void prisonYard(int x1, int x2, int z1, int z2, int stage) {
            floor(x1, x2, z1, z2, 0, Material.SMOOTH_STONE, stage);
            for (int z = z1 + 3; z <= z2 - 3; z += 5) {
                for (int x = x1 + 3; x <= x2 - 3; x += 6) block(x, 1, z, Material.IRON_BARS, BlockRole.DECORATION, stage);
            }
        }
    }
}
