package ru.neverland.townybuilds;

import ru.neverland.townybuilds.construction.BlockOffset;
import ru.neverland.townybuilds.construction.BlockRole;
import ru.neverland.townybuilds.construction.BlueprintPlan;
import ru.neverland.townybuilds.construction.BuildingBlueprintGenerator;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;

public final class BlueprintGeometrySmoke {
    private static final Set<String> CORE_BUILDINGS = Set.of(
            "town_hall", "forge", "barracks", "market", "miners_guild", "temple",
            "great_library", "agrarian_complex");

    public static void main(String[] args) {
        BuildingBlueprintGenerator generator = new BuildingBlueprintGenerator();
        Set<String> signatures = new HashSet<>();
        for (String project : CORE_BUILDINGS) {
            int previousSize = 0;
            BlueprintPlan previousPlan = null;
            for (int level = 1; level <= 5; level++) {
                int stage = level;
                BlueprintPlan plan = generator.generate(project, level);
                check(plan != null, project + ": план не создан");
                check(plan.blocks().size() > previousSize, project + ": уровень " + level + " не расширяет здание");
                check(plan.blocks().values().stream().anyMatch(block -> block.stage() == stage && block.role() == BlockRole.RESIDENT),
                        project + ": на этапе " + level + " нечего строить жителям");
                check(plan.blocks().values().stream().anyMatch(block -> block.stage() == stage && block.role() == BlockRole.DECORATION),
                        project + ": на этапе " + level + " отсутствует автоматическая отделка");
                if (previousPlan != null) {
                    for (var old : previousPlan.blocks().entrySet()) {
                        check(old.getValue().equals(plan.blocks().get(old.getKey())),
                                project + ": уровень " + level + " изменяет уже завершённый блок " + old.getKey());
                    }
                }
                previousSize = plan.blocks().size();
                previousPlan = plan;
            }
            BlueprintPlan finalPlan = generator.generate(project, 5);
            BlueprintPlan architecture2 = generator.generateArchitecture2(project, 5);
            BlueprintPlan architecture3 = generator.generateArchitecture3(project, 5);
            BlueprintPlan legacyPlan = generator.generateLegacy(project, 5);
            check(!finalPlan.blocks().equals(architecture2.blocks()), project + ": архитектура 5 совпала с 0.3.0–0.3.1");
            check(!finalPlan.blocks().equals(architecture3.blocks()), project + ": архитектура 5 совпала с 0.3.2");
            check(!finalPlan.blocks().equals(legacyPlan.blocks()), project + ": новая архитектура совпала со старой");
            for (var entry : architecture3.blocks().entrySet()) {
                if (entry.getValue().role() != BlockRole.RESIDENT) continue;
                check(entry.getValue().equals(finalPlan.blocks().get(entry.getKey())),
                        project + ": закрытие стыков изменило прогресс жителей в точке " + entry.getKey());
            }
            check(generator.generateForArchitecture(project, 5, 1).blocks().equals(legacyPlan.blocks()),
                    project + ": неверно выбран чертёж архитектуры 1");
            check(generator.generateForArchitecture(project, 5, 2).blocks().equals(architecture2.blocks()),
                    project + ": неверно выбран чертёж архитектуры 2");
            check(generator.generateForArchitecture(project, 5, 3).blocks().equals(architecture3.blocks()),
                    project + ": неверно выбран чертёж архитектуры 3");
            check(generator.generateForArchitecture(project, 5, 5).blocks().equals(finalPlan.blocks()),
                    project + ": неверно выбран актуальный чертёж");
            BlueprintPlan entrance = generator.generate(project, 1);
            long lowerDoors = entrance.blocks().values().stream()
                    .filter(block -> block.material().name().endsWith("_DOOR"))
                    .filter(block -> block.half() == Bisected.Half.BOTTOM).count();
            long upperDoors = entrance.blocks().values().stream()
                    .filter(block -> block.material().name().endsWith("_DOOR"))
                    .filter(block -> block.half() == Bisected.Half.TOP).count();
            check(lowerDoors >= 1 && lowerDoors == upperDoors, project + ": отсутствует полноценный вход с дверью");
            check(entrance.blocks().values().stream().anyMatch(block -> block.material() == org.bukkit.Material.GLASS_PANE),
                    project + ": отсутствуют окна");
            check(entrance.blocks().values().stream().anyMatch(block -> block.material().name().endsWith("_STAIRS")
                            && (block.facing() == BlockFace.EAST || block.facing() == BlockFace.WEST)),
                    project + ": крыша не использует правильно направленные ступени");
            int minX = finalPlan.blocks().keySet().stream().mapToInt(BlockOffset::x).min().orElseThrow();
            int maxX = finalPlan.blocks().keySet().stream().mapToInt(BlockOffset::x).max().orElseThrow();
            int minZ = finalPlan.blocks().keySet().stream().mapToInt(BlockOffset::z).min().orElseThrow();
            int maxZ = finalPlan.blocks().keySet().stream().mapToInt(BlockOffset::z).max().orElseThrow();
            int maxY = finalPlan.blocks().keySet().stream().mapToInt(BlockOffset::y).max().orElseThrow();
            String signature = (maxX - minX + 1) + "x" + (maxZ - minZ + 1) + "x" + (maxY + 1)
                    + ":" + finalPlan.blocks().size();
            check(signatures.add(signature), project + ": геометрическая сигнатура совпала с другим зданием: " + signature);
        }
        check(signatures.size() == 8, "Не все восемь архитектур уникальны");
        System.out.println("BlueprintGeometrySmoke OK: " + signatures);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
