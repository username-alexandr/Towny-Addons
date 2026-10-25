package ru.neverland.mintevents;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.mintevents.model.RaidState;
import ru.neverland.mintevents.service.*;

public final class RaidScalingSmoke {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static YamlConfiguration yaml(String resource) throws Exception {
        var yaml = new YamlConfiguration();
        try (var in = new InputStreamReader(Objects.requireNonNull(RaidScalingSmoke.class.getResourceAsStream(resource)), StandardCharsets.UTF_8)) { yaml.load(in); }
        return yaml;
    }
    private static Map<String, Integer> levels(int score) {
        var result = new LinkedHashMap<String, Integer>();
        for (int i = 0; score > 0; i++, score -= 5) result.put("building_" + i, Math.min(score, 5));
        return result;
    }
    public static void main(String[] args) throws Exception {
        var settings = yaml("/raid-scaling.yml"); var scaling = new RaidScaling(settings);
        var catalog = new RaidCatalog(yaml("/raids.yml"));
        var fresh = scaling.profile(Map.of(), true);
        for (int wave = 1; wave <= RaidState.WAVES; wave++) check(scaling.scale(catalog.wave(wave), fresh).equals(catalog.wave(wave)), "undeveloped town retains original wave");
        var profile = scaling.profile(Map.of("town_hall", 5, "forge", 3, "sun_pyramid", 1), true);
        check(profile.score() == 18 && profile.buildings() == 3 && profile.levels() == 9, "completed levels and wonder weights");
        check(scaling.profile(Map.of("town_hall", -4), true).score() == 0, "negative level contributes nothing");
        check(scaling.profile(Map.of("town_hall", Integer.MAX_VALUE), true).score() == 5, "bounded level");
        for (int wave = 1; wave <= RaidState.WAVES; wave++) {
            RaidCatalog.Wave previous = catalog.wave(wave); var base = catalog.wave(wave);
            for (int score = 0; score <= 250; score++) {
                var scaled = scaling.scale(base, scaling.profile(levels(score), true));
                check(scaled.mobs().size() >= previous.mobs().size() && scaled.healthMultiplier() >= previous.healthMultiplier()
                        && scaled.damageMultiplier() >= previous.damageMultiplier(), "monotonic development " + wave + ":" + score);
                check(scaled.mobs().subList(0, base.mobs().size()).equals(base.mobs()), "base enemies never replaced");
                check(scaled.mobs().size() <= 60, "default wave limit");
                check(Collections.frequency(scaled.mobs(), "captain") == Collections.frequency(base.mobs(), "captain"), "captain not multiplied");
                previous = scaled;
            }
        }
        var maximum = scaling.profile(levels(200), true);
        check(maximum.countMultiplier() == 2.5 && maximum.healthMultiplier() == 2 && maximum.damageMultiplier() == 1.5, "configured maximum");
        check(scaling.plan(catalog, maximum).equals(scaling.plan(catalog, scaling.profile(levels(450), true))), "development ceiling");
        double priorThreat = 0;
        for (var wave : scaling.plan(catalog, maximum)) {
            double threat = wave.mobs().stream().map(catalog::mob).mapToDouble(m -> m.health() * m.damage() * wave.healthMultiplier() * wave.damageMultiplier()).sum();
            check(threat > priorThreat, "successive waves still increase in difficulty"); priorThreat = threat;
        }
        settings.set("maximum.wave-size", 5); var capped = new RaidScaling(settings);
        check(capped.scale(catalog.wave(1), maximum).mobs().size() == 5, "small configured cap");
        check(capped.scale(catalog.wave(10), maximum).mobs().equals(catalog.wave(10).mobs()), "cap never deletes existing enemies");
        settings.set("enabled", false); var disabled = new RaidScaling(settings);
        check(disabled.profile(levels(200), true).countMultiplier() == 1, "disabled means original difficulty");
        check(scaling.profile(Map.of(), false).source().equals("unavailable") && scaling.profile(Map.of(), false).healthMultiplier() == 1, "provider unavailable baseline");

        var state = new RaidState(maximum, scaling.plan(catalog, maximum));
        state.begin(state.waveDefinition(1, catalog).mobs());
        UUID dead = UUID.randomUUID(), survivor = UUID.randomUUID(); state.spawned(dead); state.spawned(survivor);
        state.died(dead); int remaining = state.remaining();
        var saved = new YamlConfiguration(); state.save(saved);
        var disk = new YamlConfiguration(); disk.loadFromString(saved.saveToString());
        var loaded = RaidState.load(disk);
        check(loaded.profile().equals(maximum), "profile survives persisted YAML");
        check(loaded.remaining() == remaining && loaded.aliveCount() == 0 && !loaded.generation().equals(state.generation()), "restart retains only unfinished enemies");
        var changedYaml = yaml("/raids.yml"); changedYaml.set("waves.1.health-multiplier", 7);
        changedYaml.set("waves.10.mobs.zombie", 40); var changedCatalog = new RaidCatalog(changedYaml);
        for (int i = 1; i <= RaidState.WAVES; i++) check(loaded.waveDefinition(i, changedCatalog).equals(state.waveDefinition(i, catalog)), "persisted waves ignore reload/new catalog");
        while (!loaded.finished()) {
            if (loaded.clear()) loaded.begin(loaded.waveDefinition(loaded.wave() + 1, changedCatalog).mobs());
            while (loaded.next() != null) { var id = UUID.randomUUID(); loaded.spawned(id); check(loaded.died(id) != null && loaded.died(id) == null, "exactly-once wave deaths"); }
        }
        check(loaded.ratio() == 1, "ten complete waves still required");
        var legacy = new YamlConfiguration(); legacy.set("wave", 2); legacy.set("size", 2); legacy.set("pending", List.of("zombie")); legacy.set("alive", List.of("husk"));
        var old = RaidState.load(legacy);
        check(old.profile().source().equals("legacy") && old.wave() == 2 && old.remaining() == 2, "existing raid not recalculated");
        check(old.waveDefinition(3, catalog).equals(catalog.wave(3)), "legacy retains unscaled waves");
        var invalid = new YamlConfiguration(); invalid.loadFromString(saved.saveToString()); invalid.set("development.health-multiplier", Double.NaN);
        try { RaidState.load(invalid); throw new AssertionError("corrupt saved scaling accepted"); } catch (IllegalArgumentException expected) { checks++; }
        for (String missing : List.of("development", "planned-waves.7", "plan-version")) {
            var partial = new YamlConfiguration(); partial.loadFromString(saved.saveToString()); partial.set(missing, null);
            try { RaidState.load(partial); throw new AssertionError("incomplete plan accepted: " + missing); } catch (IllegalArgumentException expected) { checks++; }
        }
        for (String field : List.of("full-strength-score", "maximum.health", "maximum.count", "maximum.damage")) {
            var bad = yaml("/raid-scaling.yml"); bad.set(field, Double.NaN);
            try { new RaidScaling(bad); throw new AssertionError("invalid config accepted: " + field); } catch (IllegalArgumentException expected) { checks++; }
        }
        System.out.println("RaidScalingSmoke PASS: " + checks + " checks; monotonic scaling, caps, legacy compatibility, restart, frozen waves and exactly-once completion");
    }
}
