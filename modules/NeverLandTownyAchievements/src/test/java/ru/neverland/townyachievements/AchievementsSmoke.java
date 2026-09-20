package ru.neverland.townyachievements;

import java.util.*;
import java.nio.file.*;
import java.io.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townyachievements.config.AchievementSettings;
import ru.neverland.townyachievements.data.AchievementRepository;
import ru.neverland.townyachievements.model.*;

public final class AchievementsSmoke {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    interface Attempt { void run() throws Exception; }
    static void fails(Attempt attempt, String message) throws Exception { try { attempt.run(); } catch (Exception expected) { checks++; return; } throw new AssertionError(message); }
    static YamlConfiguration yaml(String name) throws Exception {
        var y = new YamlConfiguration(); try (var input = AchievementsSmoke.class.getResourceAsStream("/" + name)) {
            y.load(new InputStreamReader(Objects.requireNonNull(input), java.nio.charset.StandardCharsets.UTF_8));
        } return y;
    }
    public static void main(String[] args) throws Exception {
        var config = AchievementSettings.load(yaml("config.yml"), yaml("achievements.yml"));
        check(config.definitions().size() == 5, "five city achievements");
        var million = config.definitions().get("first_million");
        var p = Progress.begin(million).observe(999999, 100);
        check(!p.earned(), "threshold requires whole million");
        check(p.observe(1, 200).equals(p), "best city balance never decreases");
        var earned = p.observe(1000000, 300);
        check(earned.earnedAt() == 300 && earned.best() == 1000000, "one durable unlock");
        check(earned.observe(9000000, 900).equals(earned), "repeat observations cannot create rewards");
        fails(() -> new Progress(million, 0, 1), "forged completion rejected");
        fails(() -> new Progress(million, 1000000, 0), "missing completion timestamp rejected");
        fails(() -> p.observe(-1, 100), "negative observation rejected");
        var all = config.definitions().get("all_buildings_v");
        check(all.target() == 90 && all.projects().size() == 90, "all ninety standard buildings, not owned subset");
        var partial = Progress.begin(all).observe(89, 100);
        for (int i = 0; i < 100; i++) partial = partial.observe(89, 101+i);
        check(!partial.earned() && partial.best() == 89, "separate snapshots never accumulate into all buildings");
        check(partial.observe(90, 300).earned(), "all buildings at V simultaneously unlock");
        var wonder = config.definitions().get("first_wonder");
        check(wonder.projects().size() == 11 && Collections.disjoint(all.projects(), wonder.projects()), "wonders separate from buildings");
        var city = CityAchievements.empty().put(earned).put(Progress.begin(wonder).observe(1, 200));
        check(city.happiness() == 2, "wonder grants real happiness bonus");
        check(city.paused(true).happiness() == 2, "pause keeps earned benefits");
        fails(() -> city.title("raid_veterans"), "unearned title denied");
        check(city.title("first_million").title().equals("first_million"), "earned city title selected");
        persistence(city.title("first_million"), p);
        var bad = yaml("achievements.yml"); bad.set("achievements.all_buildings_v.projects", List.of());
        fails(() -> AchievementSettings.load(yaml("config.yml"), bad), "empty all-buildings catalogue cannot auto-complete");
        var reward = yaml("achievements.yml"); reward.set("achievements.first_wonder.reward.happiness", Double.NaN);
        fails(() -> AchievementSettings.load(yaml("config.yml"), reward), "nonfinite reward rejected");
        var fraction = yaml("achievements.yml"); fraction.set("achievements.first_million.target", 1.5);
        fails(() -> AchievementSettings.load(yaml("config.yml"), fraction), "fractional target rejected");
        System.out.println("AchievementsSmoke PASS: " + checks + " assertions");
    }
    static void persistence(CityAchievements earned, Progress partial) throws Exception {
        Path dir = Files.createTempDirectory("achievements-"); UUID town = UUID.randomUUID(), other = UUID.randomUUID(), player = UUID.randomUUID();
        try {
            Path file = dir.resolve("achievements-data.yml"); var repo = new AchievementRepository(file);
            fails(() -> repo.put(town, earned), "cannot write before load"); repo.load();
            repo.put(town, CityAchievements.empty().put(partial));
            var reopened = new AchievementRepository(file); reopened.load();
            check(reopened.get(town).progress().get("first_million").equals(partial), "partial progress and definition restored");
            check(reopened.get(other).progress().isEmpty(), "another city never inherits achievements");
            fails(() -> reopened.cosmetic(player, new AchievementRepository.Cosmetic(town,"first_million")), "unearned cosmetic rejected");
            reopened.put(town, earned.paused(true)); reopened.cosmetic(player, new AchievementRepository.Cosmetic(town,"first_million"));
            var saved = new AchievementRepository(file); saved.load();
            check(saved.get(town).equals(earned.paused(true)), "title, rewards and pause restored");
            check(saved.cosmetics().get(player).town().equals(town), "cosmetic bound to town UUID");
            String bytes = Files.readString(file); saved.put(town, earned.paused(true));
            check(Files.readString(file).equals(bytes), "repeat checks do not rewrite unchanged state");
            Files.delete(file); Files.createDirectory(file); Files.writeString(file.resolve("keep"), "keep");
            fails(() -> saved.put(town, earned), "failed commit reaches caller");
            check(saved.get(town).paused(), "failed write cannot change committed state");
            Files.delete(file.resolve("keep")); Files.delete(file);
            fails(() -> saved.put(town, earned), "failed storage stays closed until reload");
            Files.writeString(file, bytes); var recovered = new AchievementRepository(file); recovered.load();
            check(recovered.get(town).progress().get("first_wonder").definition().reward().happiness() == 2, "reward snapshot independent of current catalogue");
            Files.writeString(file, "schema: 1\ntowns: [broken"); var corrupt = new AchievementRepository(file);
            fails(corrupt::load, "corrupt load rejected"); fails(() -> corrupt.put(town, earned), "corrupt data not overwritten");
            check(Files.readString(file).contains("[broken"), "corruption evidence preserved");
        } finally { try (var paths = Files.walk(dir)) { for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); } }
    }
}
