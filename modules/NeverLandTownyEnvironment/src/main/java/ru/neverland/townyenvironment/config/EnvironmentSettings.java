package ru.neverland.townyenvironment.config;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import ru.neverland.core.SafeYaml;

public record EnvironmentSettings(int interval, double naturalRecovery, double threshold, double maximumHappinessPenalty,
                                  double maximumAgriculturePenalty, Map<String, Profile> buildings, Set<String> agriculture) {
    public record Profile(String name, String icon, double emission, double cleaning) {
        public Profile {
            if (name == null || name.isBlank() || icon == null || !icon.matches("[A-Z_]+")) throw new IllegalArgumentException("Неверный профиль здания");
            bound(emission, 0, 10); bound(cleaning, 0, 10);
        }
    }
    public EnvironmentSettings {
        if (interval < 30 || interval > 3600 || interval % 5 != 0) throw new IllegalArgumentException("Цикл: 30–3600 секунд, кратно 5");
        bound(naturalRecovery, 0, 10); bound(threshold, 0, 99); bound(maximumHappinessPenalty, 0, 30); bound(maximumAgriculturePenalty, 0, .8);
        if (buildings.isEmpty() || buildings.size() > 256 || agriculture.isEmpty()) throw new IllegalArgumentException("Нужен каталог экологических зданий и сельского хозяйства");
        buildings.keySet().forEach(EnvironmentSettings::id); agriculture.forEach(EnvironmentSettings::id);
        buildings = Collections.unmodifiableMap(new LinkedHashMap<>(buildings)); agriculture = Set.copyOf(agriculture);
    }
    private static void id(String id) { if (id == null || !id.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("Неверный ID здания"); }
    private static void bound(double n, double min, double max) { if (!Double.isFinite(n) || n < min || n > max) throw new IllegalArgumentException("Коэффициент вне диапазона: " + min + "…" + max); }
    public static ConfigurationSection section(ConfigurationSection root, String key) {
        var s = SafeYaml.section(root, key); if (s == null) throw new IllegalArgumentException("Отсутствует раздел " + key); return s;
    }
    public static EnvironmentSettings load(ConfigurationSection config) {
        Map<String, Profile> profiles = new LinkedHashMap<>(); var root = section(config, "buildings");
        for (String id : root.getKeys(false)) {
            var s = section(root, id); profiles.put(id, new Profile(SafeYaml.text(s, "name"), SafeYaml.text(s, "icon"), SafeYaml.doubleValue(s, "emission"), SafeYaml.doubleValue(s, "cleaning")));
        }
        return new EnvironmentSettings(Math.toIntExact(SafeYaml.integer(config, "cycle-seconds")), SafeYaml.doubleValue(config, "natural-recovery"),
                SafeYaml.doubleValue(config, "penalties.threshold"), SafeYaml.doubleValue(config, "penalties.maximum-happiness"),
                SafeYaml.doubleValue(config, "penalties.maximum-agriculture"), profiles, new HashSet<>(SafeYaml.strings(config, "agriculture")));
    }
}
