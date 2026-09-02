package ru.neverland.townybuilds;

import ru.neverland.townybuilds.construction.BuildingBlueprintGenerator;

/** Диагностический экспорт геометрии для локального визуального контроля. */
public final class BlueprintDump {
    public static void main(String[] args) {
        String project = args.length > 0 ? args[0] : "town_hall";
        int level = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        var plan = new BuildingBlueprintGenerator().generate(project, level);
        if (plan == null) throw new IllegalArgumentException(project);
        plan.blocks().forEach((position, block) -> System.out.println(position.x() + "\t" + position.y() + "\t"
                + position.z() + "\t" + block.material().name()));
    }
}
