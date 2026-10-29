package ru.neverland.townyachievements;

import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townyachievements.config.AchievementSettings;
import ru.neverland.townyachievements.data.AchievementRepository;
import ru.neverland.townyachievements.model.*;

/** Real schema-1 YAML round trips and edits; no simulated money payouts. */
public final class AchievementTiersSmoke {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    interface Attempt { void run() throws Exception; }
    static void fails(Attempt action, String message) throws Exception { try { action.run(); } catch (Exception expected) { checks++; return; } throw new AssertionError(message); }
    static YamlConfiguration yaml(String name) throws Exception { return AchievementsSmoke.yaml(name); }
    static final Set<String> LEGACY = Set.of("first_million", "population_1000", "raid_veterans", "all_buildings_v", "first_wonder");
    public static void main(String[] args) throws Exception {
        var bundled = yaml("achievements.yml"); var config = yaml("config.yml"); var settings = AchievementSettings.load(config, bundled);
        var legacy = yaml("achievements.yml");
        for (String id : new HashSet<>(legacy.getConfigurationSection("achievements").getKeys(false))) if (!LEGACY.contains(id)) legacy.set("achievements." + id, null);
        check(AchievementSettings.load(config, legacy).definitions().size() == 5, "original catalogue still loads alone");
        String unchanged = legacy.saveToString();
        var expanded = AchievementSettings.loadWithAdditions(config, legacy, bundled);
        check(expanded.definitions().size() == 23, "old catalogue receives all additional IDs");
        check(legacy.saveToString().equals(unchanged), "default merge does not rewrite source config");
        for (String id : LEGACY) check(expanded.definitions().get(id).equals(AchievementSettings.load(config, legacy).definitions().get(id)), "legacy definition exact: " + id);
        check(expanded.definitions().get("first_million").tier() == 3, "legacy milestone appears as highest common tier");
        config.set("include-new-default-goals", false);
        check(AchievementSettings.loadWithAdditions(config, legacy, bundled).definitions().size() == 5, "administrator can opt out of new defaults");
        config.set("include-new-default-goals", true);
        legacy.set("achievements.first_million", null);
        check(!AchievementSettings.loadWithAdditions(config, legacy, bundled).definitions().containsKey("first_million"), "original deleted ID is not resurrected");
        var custom = yaml("achievements.yml"); custom.set("achievements.trade_tier_1.reward.title", "Мой особый титул"); custom.set("achievements.trade_tier_1.minimum-level", 2);
        var merged = AchievementSettings.loadWithAdditions(config, custom, bundled).definitions().get("trade_tier_1");
        check(merged.reward().title().equals("Мой особый титул") && merged.minimumLevel() == 2, "whole existing ID wins over shipped defaults");
        custom.set("achievements.trade_tier_1.kind", null);
        try { AchievementSettings.loadWithAdditions(config, custom, bundled); throw new AssertionError("missing mandatory kind silently repaired"); }
        catch (IllegalArgumentException expected) { check(expected.getMessage().contains("achievements.trade_tier_1"), "bad definition names exact ID"); }
        for (var track : Achievement.Track.values()) if (track != Achievement.Track.COMMON) {
            var goals = settings.definitions().values().stream().filter(a -> a.track() == track).sorted(Comparator.comparingInt(Achievement::tier)).toList();
            check(goals.size() == 3, "three tiers in " + track);
            for (int index = 0; index < 3; index++) {
                var goal = goals.get(index);
                check(goal.tier() == index + 1 && goal.minimumLevel() == new int[]{1,3,5}[index], "tier difficulty in " + track);
                check(goal.kind() == Achievement.Kind.BUILDING_SET && goal.projects().size() == goal.target(), "fixed complete set in " + track);
                var partial = Progress.begin(goal).observe(goal.target() - 1, 10);
                for (int i = 0; i < 10; i++) partial = partial.observe(goal.target() - 1, 11 + i);
                check(!partial.earned(), "observations cannot union different incomplete sets in " + track);
            }
        }
        check(settings.definitions().values().stream().filter(a -> !LEGACY.contains(a.id())).allMatch(a -> a.reward().happiness() == 0), "new goals do not multiply city bonuses");
        for (String path : List.of("tier", "minimum-level")) {
            var bad = yaml("achievements.yml"); bad.set("achievements.trade_tier_1." + path, 0);
            fails(() -> AchievementSettings.load(config, bad), "zero " + path + " rejected");
            bad.set("achievements.trade_tier_1." + path, 6);
            fails(() -> AchievementSettings.load(config, bad), "excessive " + path + " rejected");
        }
        var bad = yaml("achievements.yml"); bad.set("achievements.trade_tier_1.projects", List.of("market", "market"));
        fails(() -> AchievementSettings.load(config, bad), "duplicate project cannot satisfy a group twice");
        persistence(settings);
        System.out.println("AchievementTiersSmoke PASS: " + checks + " assertions");
    }
    static void persistence(AchievementSettings settings) throws Exception {
        Path dir = Files.createTempDirectory("achievement-tiers-"); UUID town = UUID.randomUUID(), resident = UUID.randomUUID();
        try {
            Path file = dir.resolve("achievements-data.yml"); var repo = new AchievementRepository(file); repo.load();
            var million = Progress.begin(settings.definitions().get("first_million")).observe(1_000_000, 123);
            var wonder = Progress.begin(settings.definitions().get("first_wonder")).observe(1, 456);
            var original = CityAchievements.empty().put(million).put(wonder).title("first_million");
            repo.put(town, original); repo.cosmetic(resident, new AchievementRepository.Cosmetic(town, "first_million"));
            var data = new YamlConfiguration(); data.load(file.toFile());
            String prefix = "towns." + town + ".progress.";
            for (String id : LEGACY) for (String field : List.of("track", "tier", "minimum-level")) data.set(prefix + id + ".definition." + field, null);
            data.save(file.toFile()); // exact old schema: definitions have no new metadata.
            var old = new AchievementRepository(file); old.load();
            check(old.get(town).equals(original), "schema-1 legacy rewards/title/timestamps restored unchanged");
            check(old.cosmetics().get(resident).achievement().equals("first_million"), "legacy cosmetic selection preserved");
            var upgraded = old.get(town);
            for (var goal : settings.definitions().values()) if (!LEGACY.contains(goal.id())) upgraded = upgraded.put(Progress.begin(goal).observe(goal.target(), 1000));
            old.put(town, upgraded.paused(true));
            var reopened = new AchievementRepository(file); reopened.load(); var state = reopened.get(town);
            check(state.paused() && state.title().equals("first_million"), "new tiers preserve selected title and pause on restart");
            check(state.happiness() == 2, "all eighteen tiers together add no happiness inflation");
            check(state.progress().get("first_million").equals(million), "no re-earned legacy timestamp");
            var savedTier = state.progress().get("trade_tier_1");
            var altered = yaml("achievements.yml"); altered.set("achievements.trade_tier_1.minimum-level", 5); altered.set("achievements.trade_tier_1.reward.title", "Переписанная награда");
            var current = AchievementSettings.load(yaml("config.yml"), altered);
            var visible = new LinkedHashMap<String, Progress>(); current.definitions().forEach((id, a) -> visible.put(id, Progress.begin(a))); visible.putAll(state.progress());
            check(visible.get("trade_tier_1").equals(savedTier), "persisted definition wins over changed catalogue");
            check(savedTier.observe(0, 2000).equals(savedTier), "lost provider or reduced metric cannot revoke earned tier");
            check(state.title("trade_tier_1").title().equals("trade_tier_1"), "one selected title replaces previous, without stacking");
            String bytes = Files.readString(file); reopened.put(town, state);
            check(Files.readString(file).equals(bytes), "restart/no-change does not rewrite state or replay earned rewards");
        } finally { try (var files = Files.walk(dir)) { for (var f : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(f); } }
    }
}
