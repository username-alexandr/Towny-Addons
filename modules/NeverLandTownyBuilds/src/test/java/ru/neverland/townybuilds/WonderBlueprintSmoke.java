package ru.neverland.townybuilds;

import org.bukkit.Material;
import org.bukkit.block.data.Bisected;
import ru.neverland.townybuilds.construction.BlockOffset;
import ru.neverland.townybuilds.construction.BlockRole;
import ru.neverland.townybuilds.construction.BlueprintPlan;
import ru.neverland.townybuilds.construction.BuildingBlueprintGenerator;

import java.util.HashSet;
import java.util.Set;

public final class WonderBlueprintSmoke {
    public static void main(String[] args) {
        BuildingBlueprintGenerator generator = new BuildingBlueprintGenerator();
        check(generator.supportedWonders().size() == 5, "Должно быть ровно пять Чудес Света");
        Set<String> signatures = new HashSet<>();
        for (String wonder : generator.supportedWonders()) {
            BlueprintPlan plan = generator.generate(wonder, 1);
            check(plan != null, wonder + ": чертёж не создан");
            check(generator.maximumStage(wonder) == 1, wonder + ": у Чуда появился лишний уровень");
            check(plan.residentBlocks(1) >= 250, wonder + ": слишком мало блоков для жителей");
            check(plan.decorationBlocks(1) >= 25, wonder + ": недостаточно автоматической отделки");
            check(plan.blocks().values().stream().anyMatch(block -> block.material().name().endsWith("_STAIRS")),
                    wonder + ": отсутствуют лестницы или ступени");
            check(plan.blocks().keySet().stream().mapToInt(BlockOffset::y).min().orElseThrow() == 0,
                    wonder + ": основание не лежит на Y=0");

            int minX = plan.blocks().keySet().stream().mapToInt(BlockOffset::x).min().orElseThrow();
            int maxX = plan.blocks().keySet().stream().mapToInt(BlockOffset::x).max().orElseThrow();
            int minZ = plan.blocks().keySet().stream().mapToInt(BlockOffset::z).min().orElseThrow();
            int maxZ = plan.blocks().keySet().stream().mapToInt(BlockOffset::z).max().orElseThrow();
            int maxY = plan.blocks().keySet().stream().mapToInt(BlockOffset::y).max().orElseThrow();
            String signature = (maxX - minX + 1) + "x" + (maxZ - minZ + 1) + "x" + (maxY + 1)
                    + ":" + plan.blocks().size();
            check(signatures.add(signature), wonder + ": геометрия совпала с другим Чудом");

            if (!wonder.equals("great_colosseum") && !wonder.equals("hanging_gardens")) {
                long lowerDoors = plan.blocks().values().stream()
                        .filter(block -> block.material().name().endsWith("_DOOR"))
                        .filter(block -> block.half() == Bisected.Half.BOTTOM).count();
                long upperDoors = plan.blocks().values().stream()
                        .filter(block -> block.material().name().endsWith("_DOOR"))
                        .filter(block -> block.half() == Bisected.Half.TOP).count();
                check(lowerDoors >= 1 && lowerDoors == upperDoors, wonder + ": отсутствует полноценная дверь");
            }
            check(plan.blocks().values().stream().anyMatch(block -> block.role() == BlockRole.RESIDENT),
                    wonder + ": нет ручного этапа");
            check(plan.blocks().values().stream().anyMatch(block -> block.role() == BlockRole.DECORATION),
                    wonder + ": нет автоматической отделки");
            check(plan.blocks().values().stream().noneMatch(block -> block.material() == Material.AIR),
                    wonder + ": AIR попал в план");
            System.out.println(wonder + " -> " + signature + ", жители=" + plan.residentBlocks(1)
                    + ", декор=" + plan.decorationBlocks(1));
        }
        System.out.println("WonderBlueprintSmoke OK: " + signatures);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
