package ru.neverland.townyenvironment;

import java.util.*;
import java.nio.file.*;
import java.io.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townyenvironment.config.EnvironmentSettings;
import ru.neverland.townyenvironment.data.EnvironmentRepository;
import ru.neverland.townyenvironment.model.*;

public final class EnvironmentSmoke {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    interface Attempt { void run() throws Exception; }
    static void fails(Attempt action, String message) throws Exception { try { action.run(); } catch (Exception expected) { checks++; return; } throw new AssertionError(message); }
    static YamlConfiguration config() throws Exception {
        var y = new YamlConfiguration(); try (var input = EnvironmentSmoke.class.getResourceAsStream("/config.yml")) {
            y.load(new InputStreamReader(Objects.requireNonNull(input), java.nio.charset.StandardCharsets.UTF_8));
        } return y;
    }
    static void near(double actual, double expected, String message) { check(Math.abs(actual - expected) < 1e-9, message); }
    public static void main(String[] args) throws Exception {
        var settings = EnvironmentSettings.load(config());
        check(settings.buildings().size() == 20 && settings.agriculture().size() == 7, "complete ecological catalogue");
        var industry = EnvironmentEngine.pressure(Map.of("foundry", 5, "industrial_works", 5), settings);
        near(industry.emissions(), 4.5, "five levels industrial emissions"); near(industry.change(), 4.3, "natural recovery deducted once");
        var clean = EnvironmentEngine.pressure(Map.of("park", 5, "forestry", 5), settings);
        near(clean.cleaning(), 3.45, "parks and forestry add cleaning");
        var polluted = EnvironmentEngine.advance(EnvironmentState.clean(), industry); near(polluted.pollution(), 4.3, "industry increases pollution");
        near(EnvironmentEngine.advance(polluted, clean).pollution(), .85, "green areas lower pollution");
        near(EnvironmentEngine.advance(EnvironmentState.clean(), clean).pollution(), 0, "no negative pollution");
        near(EnvironmentEngine.advance(new EnvironmentState(99, 10, false), industry).pollution(), 100, "maximum 100");
        var threshold = EnvironmentEngine.effects(new EnvironmentState(40, 0, false), settings, true);
        near(threshold.happiness(), 0, "threshold neutral"); near(threshold.agriculture(), 1, "agriculture threshold neutral");
        var half = EnvironmentEngine.effects(new EnvironmentState(70, 0, false), settings, true);
        near(half.happiness(), -10, "linear happiness penalty"); near(half.agriculture(), .8, "linear agriculture penalty");
        var worst = EnvironmentEngine.effects(new EnvironmentState(100, 0, false), settings, true);
        near(worst.happiness(), -20, "happiness bound"); near(worst.agriculture(), .6, "production never stops completely");
        var paused = new EnvironmentState(90, 77, true);
        check(EnvironmentEngine.advance(paused, industry).equals(paused), "pause preserves pollution and cycle count");
        near(EnvironmentEngine.effects(paused, settings, true).happiness(), 0, "admin pause removes penalty");
        near(EnvironmentEngine.effects(paused.paused(false), settings, false).agriculture(), 1, "provider outage neutral, no losses");
        fails(() -> new EnvironmentState(Double.NaN, 0, false), "NaN pollution rejected");
        fails(() -> new EnvironmentState(100.1, 0, false), "out-of-range pollution rejected");
        fails(() -> EnvironmentEngine.pressure(Map.of("foundry", 6), settings), "forged level rejected");
        var bad = config(); bad.set("penalties.maximum-agriculture", 1);
        fails(() -> EnvironmentSettings.load(bad), "configuration cannot erase all food output");
        var badCycle = config(); badCycle.set("cycle-seconds", 31);
        fails(() -> EnvironmentSettings.load(badCycle), "cycle must match scheduler step");
        persistence(paused);
        System.out.println("EnvironmentSmoke PASS: " + checks + " assertions");
    }
    static void persistence(EnvironmentState state) throws Exception {
        var directory = Files.createTempDirectory("environment-"); UUID town = UUID.randomUUID();
        try {
            var file = directory.resolve("environment-data.yml"); var repository = new EnvironmentRepository(file);
            fails(() -> repository.put(town, state), "write before successful load denied"); repository.load(); repository.put(town, state);
            var restored = new EnvironmentRepository(file); restored.load(); check(restored.get(town).equals(state), "pollution, cycles and admin pause survive restart");
            check(restored.get(UUID.randomUUID()).equals(EnvironmentState.clean()), "new city cannot inherit deleted city's pollution");
            String saved = Files.readString(file); restored.put(town, state); check(Files.readString(file).equals(saved), "unchanged data stable");
            Files.delete(file); Files.createDirectory(file); Files.writeString(file.resolve("keep"), "keep");
            fails(() -> restored.put(town, state.paused(false)), "disk failure reaches caller"); check(restored.get(town).equals(state), "failed write never publishes state");
            Files.delete(file.resolve("keep")); Files.delete(file);
            fails(() -> restored.put(town, state.paused(false)), "storage stays blocked after write failure");
            Files.writeString(file, saved); var valid = new EnvironmentRepository(file); valid.load(); check(valid.get(town).equals(state), "no offline catchup after reload");
            Files.writeString(file, "schema: 1\ntowns: [broken"); var corrupt = new EnvironmentRepository(file);
            fails(corrupt::load, "corrupt YAML rejected"); fails(() -> corrupt.put(town, state), "corrupt data cannot be overwritten");
            check(Files.readString(file).contains("[broken"), "corrupt evidence preserved");
            Files.writeString(file, saved.replace("pollution: 90.0", "pollution: .NaN")); var invalid = new EnvironmentRepository(file);
            fails(invalid::load, "nonfinite persisted state rejected");
        } finally { try (var paths = Files.walk(directory)) { for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); } }
    }
}
