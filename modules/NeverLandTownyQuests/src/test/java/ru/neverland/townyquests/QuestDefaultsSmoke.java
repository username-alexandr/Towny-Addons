package ru.neverland.townyquests;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townyquests.config.QuestSettings;
import ru.neverland.townyquests.model.Project;

/** Regression for upgrading a saved two-project configuration to the bundled four-project file. */
public final class QuestDefaultsSmoke {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static YamlConfiguration resource(String name) throws Exception {
        var result = new YamlConfiguration();
        try (var input = QuestDefaultsSmoke.class.getResourceAsStream("/" + name)) {
            result.load(new InputStreamReader(Objects.requireNonNull(input), StandardCharsets.UTF_8));
        }
        return result;
    }
    private static YamlConfiguration legacy() throws Exception {
        var saved = resource("quests.yml");
        saved.set("projects.industrial_future", null);
        saved.set("projects.green_future", null);
        saved.setDefaults(resource("quests.yml"));
        saved.options().copyDefaults(true);
        return saved;
    }
    private static void rejects(YamlConfiguration yaml, String path, String value) throws Exception {
        try {
            QuestSettings.load(resource("config.yml"), yaml);
        } catch (IllegalArgumentException ex) {
            check(ex.getMessage().contains(path), "invalid type identifies its configuration path: " + ex);
            check(ex.getMessage().contains(value), "invalid type reports the supplied value: " + ex);
            return;
        }
        throw new AssertionError("Invalid stage type accepted: " + path);
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("baseline")) {
            try {
                QuestSettings.load(resource("config.yml"), legacy());
            } catch (IllegalArgumentException ex) {
                check(ex.getMessage().startsWith("No enum constant "), "same failure as server log");
                System.out.println("REPRODUCED: " + ex);
                return;
            }
            throw new AssertionError("The original loader unexpectedly accepted inherited project stages");
        }

        var saved = legacy();
        var settings = QuestSettings.load(resource("config.yml"), saved);
        check(settings.projects().size() == 4, "old projects and new bundled branches load together");
        for (String id : List.of("industrial_future", "green_future")) {
            var branch = settings.projects().get(id);
            check(branch.stages().size() == 3, "all inherited branch stages load: " + id);
            check(branch.stages().stream().allMatch(stage -> stage.kind() == Project.Kind.BUILDING),
                    "inherited types survive explicit getter fallbacks: " + id);
            check(branch.choiceGroup().equals("city_future"), "exclusive branch group survives: " + id);
        }
        check(settings.projects().get("public_health").stages().get(2).holdSeconds() == 600,
                "configured water duration is retained");

        var partial = legacy();
        partial.set("projects.sanitation.name", "Мой водопровод");
        partial.set("projects.sanitation.stages.water.hold-seconds", null);
        partial.set("projects.sanitation.stages.aqueduct.type", null);
        partial.set("projects.sanitation.stages.aqueduct.building", null);
        partial.set("projects.sanitation.stages.aqueduct.target", 2);
        partial.set("projects.public_health.stages.water.hold-seconds", 420);
        partial.set("projects.public_health.requires", List.of());
        String before = partial.saveToString();
        var custom = QuestSettings.load(resource("config.yml"), partial);
        check(custom.projects().get("sanitation").name().equals("Мой водопровод"), "local name retained");
        var stage = custom.projects().get("sanitation").stages().get(0);
        check(stage.kind() == Project.Kind.BUILDING && stage.building().equals("aqueduct"),
                "missing type and building inherit their bundled values");
        check(stage.target() == 2, "local stage target retained");
        check(custom.projects().get("sanitation").stages().get(1).holdSeconds() == 300,
                "missing hold duration inherits its nonzero bundled value");
        check(custom.projects().get("public_health").stages().get(2).holdSeconds() == 420,
                "local duration overrides bundled duration");
        check(custom.projects().get("public_health").requires().isEmpty(), "explicit empty list retained");
        check(partial.saveToString().equals(before), "load does not modify the supplied configuration");

        var empty = new YamlConfiguration();
        empty.setDefaults(resource("quests.yml"));
        empty.options().copyDefaults(true);
        check(QuestSettings.load(resource("config.yml"), empty).projects().size() == 4,
                "a configuration supplied entirely by defaults loads");

        var config = new YamlConfiguration();
        config.set("simulation.interval-seconds", 7);
        config.setDefaults(resource("config.yml"));
        config.options().copyDefaults(true);
        var simulation = QuestSettings.load(config, resource("quests.yml"));
        check(simulation.interval() == 7 && simulation.resourceMaxAge() == 180,
                "simulation overrides and defaults merge together");

        var invalid = legacy();
        invalid.set("projects.sanitation.stages.aqueduct.type", "UNKNOWN_KIND");
        rejects(invalid, "projects.sanitation.stages.aqueduct.type", "UNKNOWN_KIND");
        var blank = legacy();
        blank.set("projects.sanitation.stages.aqueduct.type", "");
        rejects(blank, "projects.sanitation.stages.aqueduct.type", "пусто");

        var malformed = legacy();
        malformed.set("projects.sanitation.stages", "broken");
        try {
            QuestSettings.load(resource("config.yml"), malformed);
            throw new AssertionError("An explicit malformed section was replaced by defaults");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("stages"), "malformed explicit section remains rejected");
        }
        System.out.println("QuestDefaultsSmoke PASS: " + checks + " checks");
    }
}
