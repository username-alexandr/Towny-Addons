package ru.neverland.townybuilds;

import org.bukkit.Material;
import ru.neverland.townybuilds.construction.BuildingBlueprintGenerator;
import ru.neverland.townybuilds.construction.ExcavationPlanner;

public final class ExcavationPlannerSmoke {
    public static void main(String[] args) {
        BuildingBlueprintGenerator generator = new BuildingBlueprintGenerator();

        check(ExcavationPlanner.offsets(generator.generate("quarry", 1), 1).isEmpty(),
                "Первый этап каменоломни не должен выкапывать грунт");
        check(ExcavationPlanner.offsets(generator.generate("quarry", 2), 2).size() == 197,
                "Каменоломня должна подготовить стены и внутренний объём котлована");
        check(ExcavationPlanner.offsets(generator.generate("sewer", 2), 2).size() == 364,
                "Канализация должна подготовить четыре подземных слоя");

        check(ExcavationPlanner.canClear(Material.GRASS_BLOCK), "Дёрн должен расчищаться");
        check(ExcavationPlanner.canClear(Material.DIRT), "Земля должна расчищаться");
        check(ExcavationPlanner.canClear(Material.STONE), "Камень должен расчищаться");
        check(ExcavationPlanner.canClear(Material.DIAMOND_ORE), "Природная руда должна расчищаться");
        check(!ExcavationPlanner.canClear(Material.STONE_BRICKS), "Строительный блок нельзя удалять");
        check(!ExcavationPlanner.canClear(Material.CHEST), "Контейнер нельзя удалять");
        System.out.println("ExcavationPlannerSmoke OK: quarry=197, sewer=364");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
