package ru.neverland.townyquests;

import java.util.*;
import java.nio.file.*;
import java.io.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townyquests.config.QuestSettings;
import ru.neverland.townyquests.data.QuestRepository;
import ru.neverland.townyquests.model.*;
import ru.neverland.townyquests.model.ProjectEngine.Observation;
import ru.neverland.townyquests.model.WaterCoverage.Cell;

public final class QuestSmoke {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    interface Attempt { void run() throws Exception; }
    static void fails(Attempt attempt, String message) throws Exception { try { attempt.run(); } catch (Exception expected) { checks++; return; } throw new AssertionError(message); }
    static YamlConfiguration yaml(String name) throws Exception {
        var y = new YamlConfiguration(); try (var in = QuestSmoke.class.getResourceAsStream("/" + name)) {
            y.load(new InputStreamReader(Objects.requireNonNull(in), java.nio.charset.StandardCharsets.UTF_8));
        } return y;
    }
    public static void main(String[] args) throws Exception {
        var settings = QuestSettings.load(yaml("config.yml"), yaml("quests.yml"));
        check(settings.projects().size() == 2, "two real city chains");
        var project = settings.projects().get("sanitation"); var run = CityProject.start(project);
        check(run.stage() == 0 && run.status() == CityProject.Status.ACTIVE, "starts at first stage");
        check(ProjectEngine.advance(run, new Observation(true, 0, "absent"), 5).equals(run), "missing aqueduct cannot skip");
        run = ProjectEngine.advance(run, new Observation(true, 1, "built"), 0);
        check(run.stage() == 1 && run.heldSeconds() == 0, "one stage at a time");
        run = ProjectEngine.advance(run, new Observation(true, 5, "water"), 60);
        check(run.heldSeconds() == 60, "observed online interval");
        var held = run;
        check(ProjectEngine.advance(held, Observation.waiting("provider disabled"), 60).equals(held), "provider outage freezes partial hold");
        check(ProjectEngine.advance(held, new Observation(true, 5, "restored"), 0).equals(held), "restart observation grants no offline time");
        check(ProjectEngine.advance(held, new Observation(true, 4, "one missing"), 5).heldSeconds() == 0, "shortage resets continuous supply");
        check(ProjectEngine.advance(held.control("pause"), new Observation(true, 5, "water"), 60).heldSeconds() == 60, "admin pause freezes hold");
        check(held.control("cancel").stage() == 1 && held.control("cancel").heldSeconds() == 60, "neutral cancellation preserves stages and hold");
        check(held.control("cancel").control("resume").equals(held), "cancelled city project can resume without new reward");
        check(held.control("pause").control("restart").status() == CityProject.Status.PAUSED, "restart preserves pause");
        check(held.control("restart").stage() == 1 && held.control("restart").heldSeconds() == 0, "restart only resets current hold");
        for (int i = 0; i < 4; i++) run = ProjectEngine.advance(run, new Observation(true, 5, "water"), 60);
        check(run.stage() == 2, "five districts sustained 300 seconds");
        run = ProjectEngine.advance(run, new Observation(true, 1, "baths"), 0);
        check(run.status() == CityProject.Status.COMPLETED && run.definition().unlock().equals("sanitation_reform"), "final completion unlocks reform");
        check(ProjectEngine.advance(run, new Observation(true, 9, "again"), 60).equals(run), "repeated ticks cannot duplicate completion");
        var completed = run; fails(() -> completed.control("restart"), "completed projects cannot replay");
        fails(() -> new CityProject(UUID.randomUUID(), project, 0, 0, CityProject.Status.COMPLETED), "forged early completion rejected");
        fails(() -> ProjectEngine.advance(held, new Observation(true, 5, "offline"), 3600), "offline catchup rejected");
        geometry(); persistence(held, completed); configuration();
        System.out.println("QuestSmoke PASS: " + checks + " checks; city stages, water topology, safe stops, strict config and durable completion");
    }
    private static void geometry() {
        UUID world = UUID.randomUUID(), other = UUID.randomUUID(); Set<Cell> owned = new HashSet<>();
        for (int x = -2; x <= 5; x++) owned.add(new Cell(world, x, 0));
        var aqueduct = Set.of(new Cell(world, -2, 0)); Map<String, Set<Cell>> districts = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) districts.put("district" + i, Set.of(new Cell(world, i, 0)));
        check(WaterCoverage.supplied(owned, aqueduct, districts).size() == 5, "five unique connected districts, negative coordinates");
        owned.remove(new Cell(world, 3, 0));
        check(WaterCoverage.supplied(owned, aqueduct, districts).size() == 2, "unclaimed gap and disconnected outposts excluded");
        owned.add(new Cell(world, 3, 0)); districts.put("foreign", Set.of(new Cell(other, 1, 0)));
        districts.put("partial", Set.of(new Cell(world, 1, 0), new Cell(world, 1, 1)));
        districts.put("empty", Set.of());
        check(WaterCoverage.supplied(owned, aqueduct, districts).size() == 5, "whole district, ownership and world required");
        check(WaterCoverage.supplied(owned, Set.of(), districts).isEmpty(), "missing footprint gives no water");
        check(WaterCoverage.supplied(owned, Set.of(new Cell(world, -3, 0)), districts).isEmpty(), "foreign aqueduct gives no water");
        for (int i = 2; i <= 5; i++) districts.remove("district" + i);
        for (int i = 0; i < 5; i++) check(WaterCoverage.supplied(owned, aqueduct, districts).size() == 1, "same district observations never accumulate");
    }
    private static void persistence(CityProject held, CityProject completed) throws Exception {
        Path dir = Files.createTempDirectory("city-projects");
        try {
            Path file = dir.resolve("quests-data.yml"); UUID town = UUID.randomUUID(), other = UUID.randomUUID();
            var repository = new QuestRepository(file); var uninitialized = repository; fails(() -> uninitialized.put(town, held), "cannot write before load");
            repository.load(); repository.put(town, held);
            var reopened = new QuestRepository(file); reopened.load();
            check(reopened.get(town).get("sanitation").equals(held), "snapshot definition and hold survive restart");
            check(reopened.get(other).isEmpty(), "town UUID isolation");
            var another = CityProject.start(QuestSettings.load(yaml("config.yml"), yaml("quests.yml")).projects().get("public_health"));
            fails(() -> reopened.put(town, another), "only one current city project");
            Files.delete(file); Files.createDirectory(file); Files.writeString(file.resolve("keep"), "keep");
            fails(() -> reopened.put(town, completed), "failed save cannot unlock reward");
            check(reopened.get(town).get("sanitation").equals(held), "authoritative progress unchanged after failure");
            Files.delete(file.resolve("keep")); Files.delete(file);
            fails(() -> reopened.put(town, completed), "save remains blocked until valid reload");
            repository = new QuestRepository(file); repository.load(); repository.put(town, completed);
            var finalRead = new QuestRepository(file); finalRead.load();
            check(finalRead.get(town).get("sanitation").status() == CityProject.Status.COMPLETED, "unlock persists before visible completion");
            Files.writeString(file, "schema: 1\ntowns: [broken"); var broken = new QuestRepository(file);
            fails(broken::load, "corrupt YAML rejected"); fails(() -> broken.put(town, completed), "corrupt file never overwritten");
            check(Files.readString(file).contains("[broken"), "corrupt evidence preserved");
        } finally { try (var files = Files.walk(dir)) { for (var p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); } }
    }
    private static void configuration() throws Exception {
        var quests = yaml("quests.yml"); quests.set("projects.sanitation.requires", List.of("public_health"));
        fails(() -> QuestSettings.load(yaml("config.yml"), quests), "cyclic dependencies rejected");
        var unknown = yaml("quests.yml"); unknown.set("projects.sanitation.requires", List.of("absent"));
        fails(() -> QuestSettings.load(yaml("config.yml"), unknown), "missing prerequisite rejected");
        var time = yaml("quests.yml"); time.set("projects.sanitation.stages.water.hold-seconds", 0.5);
        fails(() -> QuestSettings.load(yaml("config.yml"), time), "fractional times rejected");
        var duplicate = yaml("quests.yml"); duplicate.set("projects.public_health.unlock", "sanitation_reform");
        fails(() -> QuestSettings.load(yaml("config.yml"), duplicate), "duplicate results rejected");
    }
}
