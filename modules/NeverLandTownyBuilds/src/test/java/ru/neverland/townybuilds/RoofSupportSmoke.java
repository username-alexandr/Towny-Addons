package ru.neverland.townybuilds;

import java.util.List;
import java.util.Map;
import ru.neverland.townybuilds.construction.*;

public final class RoofSupportSmoke {
    public static void main(String[] args) {
        BuildingBlueprintGenerator generator = new BuildingBlueprintGenerator();
        ImportedModelBlueprintGenerator imported = new ImportedModelBlueprintGenerator();
        int added = 0, repaired = 0;
        for (String project : ImportedModelBlueprintGenerator.PROJECTS) {
            var original = imported.generateOriginal(project, 5);
            var modern = imported.generate(project, 5);
            int difference = modern.blocks().size() - original.blocks().size();
            if (difference > 0) repaired++;
            added += difference;
            for (var entry : original.blocks().entrySet()) {
                var updated = modern.blocks().get(entry.getKey());
                check(updated != null && entry.getValue().material() == updated.material()
                        && entry.getValue().role() == updated.role() && updated.stage() <= entry.getValue().stage(), project + ": source material altered");
            }
            check(generator.generateForArchitecture(project, 5, 5).blocks().equals(original.blocks()),
                    project + ": old architecture cannot be migrated");
            for (int stage = 1; stage <= 5; stage++) {
                var plan = generator.generate(project, stage);
                for (var entry : plan.blocks().entrySet()) {
                    if (original.blocks().containsKey(entry.getKey())) continue;
                    check(entry.getValue().role() == BlockRole.RESIDENT, project + ": roof wall is decorative");
                    check(entry.getValue().stage() <= stage, project + ": future repair appears early");
                    check(entry.getValue().equals(modern.blocks().get(entry.getKey())), project + ": repair changes on upgrade");
                }
            }
        }
        for (var fixture : Map.of(
                "mill", new BlockOffset(-5, 10, -4),
                "bakery", new BlockOffset(0, 6, -4),
                "guard", new BlockOffset(-3, 7, -5),
                "cathedral", new BlockOffset(-4, 12, -9)).entrySet()) {
            check(!imported.generateOriginal(fixture.getKey(), 5).blocks().containsKey(fixture.getValue()), "Fixture no longer demonstrates hole");
            check(generator.generate(fixture.getKey(), 3).blocks().containsKey(fixture.getValue()), "Gable still open: " + fixture);
        }
        check(generator.generate("cathedral", 3).blocks().containsKey(new BlockOffset(-5, 12, -9)),
                "Crossing cathedral roofs leave an open upper gable");
        // Procedural glass gables, including the greenhouse introduced at level 3.
        for (var fixture : Map.of("agrarian_complex", new BlockOffset(-9, 4, -3),
                "crystal_palace", new BlockOffset(1, 12, -13)).entrySet()) {
            int level = generator.maximumStage(fixture.getKey());
            check(!generator.generateForArchitecture(fixture.getKey(), level, 5).blocks().containsKey(fixture.getValue()), "Old glass gable fixture");
            check(generator.generate(fixture.getKey(), level).blocks().get(fixture.getValue()).role() == BlockRole.RESIDENT, "Glass gable missing");
        }
        check(repaired == 26 && added == 1937, "Unexpected support repairs: " + repaired + "/" + added);
        int levels = 0;
        for (String project : generator.supportedProjects()) {
            for (int stage = 1; stage <= generator.maximumStage(project); stage++) {
                var plan = generator.generate(project, stage);
                check(plan != null && !plan.blocks().isEmpty(), project + ": empty stage " + stage);
                levels++;
            }
        }
        System.out.println("RoofSupportSmoke OK: " + levels + " catalog levels; 26 models, 1937 structural roof supports");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
