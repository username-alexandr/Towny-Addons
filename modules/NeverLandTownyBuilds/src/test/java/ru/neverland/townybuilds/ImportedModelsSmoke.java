package ru.neverland.townybuilds;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import ru.neverland.townybuilds.construction.BlockOffset;
import ru.neverland.townybuilds.construction.BlueprintPlan;
import ru.neverland.townybuilds.construction.BuildingBlueprintGenerator;
import ru.neverland.townybuilds.construction.ImportedModelBlueprintGenerator;

import java.util.Set;

/** Exact regression checks for the reviewed 37-model / 25,497-block pack. */
public final class ImportedModelsSmoke {
    private static final Set<String> OPEN_STRUCTURES = Set.of(
            "quarry", "sewer", "roads", "bridge_service", "square", "arena", "memorial"
    );
    private static final Set<Material> ROOF_MATERIALS = Set.of(
            Material.DEEPSLATE_TILES, Material.IRON_BLOCK, Material.CYAN_STAINED_GLASS
    );

    public static void main(String[] args) {
        BuildingBlueprintGenerator generator = new BuildingBlueprintGenerator();
        ImportedModelBlueprintGenerator imported = new ImportedModelBlueprintGenerator();
        check(imported.supportedProjects().size() == 37, "Ожидалось 37 импортированных моделей");
        check(generator.supportedBuildings().size() == 90, "Ожидалось 90 городских проектов");
        check(generator.supportedProjects().size() == 101, "Ожидалось 101 физических проектов с Чудесами");

        int totalBlocks = 0;
        long lowerDoors = 0;
        long upperDoors = 0;
        for (String project : ImportedModelBlueprintGenerator.PROJECTS) {
            BlueprintPlan previous = null;
            int stageTotal = 0;
            for (int stage = 1; stage <= 5; stage++) {
                BlueprintPlan plan = generator.generate(project, stage);
                check(plan != null, project + ": план не создан");
                int currentStage = stage;
                long added = plan.blocks().values().stream().filter(block -> block.stage() == currentStage).count();
                check(added > 0, project + ": этап " + stage + " пуст");
                stageTotal += Math.toIntExact(added);
                if (previous != null) {
                    for (var entry : previous.blocks().entrySet()) {
                        check(entry.getValue().equals(plan.blocks().get(entry.getKey())),
                                project + ": этап " + stage + " изменяет готовый блок " + entry.getKey());
                    }
                }
                previous = plan;
            }

            BlueprintPlan finalPlan = generator.generate(project, 5);
            int originalSize = ImportedModelBlueprintGenerator.SOURCE_BLOCKS.get(project);
            check(imported.generateOriginal(project, 5).blocks().size() == originalSize, project + ": original model changed");
            int expected = imported.generate(project, 5).blocks().size();
            check(finalPlan.blocks().size() == expected,
                    project + ": ожидалось " + expected + " блоков, получено " + finalPlan.blocks().size());
            check(stageTotal == expected, project + ": сумма этапов не совпала с итогом");
            check(generator.generateForArchitecture(project, 5, 6).blocks().equals(finalPlan.blocks()),
                    project + ": встроенный источник не воспроизводится");
            if (!OPEN_STRUCTURES.contains(project)) {
                check(finalPlan.blocks().values().stream().anyMatch(block -> ROOF_MATERIALS.contains(block.material())),
                        project + ": отсутствует полноценная крыша");
            }
            lowerDoors += finalPlan.blocks().values().stream()
                    .filter(block -> block.material() == Material.SPRUCE_DOOR)
                    .filter(block -> block.half() == Bisected.Half.BOTTOM)
                    .peek(block -> check(block.facing() == BlockFace.SOUTH,
                            project + ": дверь направлена не к фасаду"))
                    .count();
            upperDoors += finalPlan.blocks().values().stream()
                    .filter(block -> block.material() == Material.SPRUCE_DOOR)
                    .filter(block -> block.half() == Bisected.Half.TOP)
                    .count();
            totalBlocks += finalPlan.blocks().size();
        }

        check(totalBlocks == 27_434, "Суммарно ожидалось 27 434 блоков, получено " + totalBlocks);
        check(lowerDoors == 46 && upperDoors == 46, "Ожидалось 46 полноценных дверей");
        check(imported.isLinear("roads") && imported.isLinear("bridge_service"),
                "Дорога и мост должны быть линейными проектами");
        check(!imported.isLinear("sawmill"), "Обычное здание ошибочно помечено линейным");

        for (String linear : Set.of("roads", "bridge_service")) {
            BlueprintPlan plan = generator.generate(linear, 5);
            int minX = plan.blocks().keySet().stream().mapToInt(BlockOffset::x).min().orElseThrow();
            int maxX = plan.blocks().keySet().stream().mapToInt(BlockOffset::x).max().orElseThrow();
            check(maxX - minX + 1 == 19, linear + ": длина участка должна быть 19 блоков");
        }
        System.out.println("ImportedModelsSmoke OK: 37 моделей, 185 этапов, 27 434 блоков, 46 дверей");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
