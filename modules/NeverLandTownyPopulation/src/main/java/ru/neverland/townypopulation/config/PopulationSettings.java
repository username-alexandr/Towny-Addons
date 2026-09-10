package ru.neverland.townypopulation.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townypopulation.model.Capacity;
import ru.neverland.townypopulation.model.PopulationMath;
import java.util.*;
import java.util.function.ToIntFunction;

public record PopulationSettings(long intervalMillis, int initial, Capacity base,
                                 PopulationMath.Rules rules, Map<String, Building> buildings) {
    public PopulationSettings { buildings = Collections.unmodifiableMap(new LinkedHashMap<>(buildings)); }
    public record Building(String name, String icon, int minimumLevel, int maximumLevel, Capacity capacity) {
        public Capacity atLevel(int level) { return capacity.times(Math.max(0, Math.min(maximumLevel,level)-minimumLevel+1)); }
    }
    public Capacity capacity(ToIntFunction<String> levels) {
        return capacity(levels,id -> 1.0);
    }
    public Capacity capacity(ToIntFunction<String> levels, java.util.function.ToDoubleFunction<String> multipliers) {
        Capacity result = base;
        for (var entry : buildings.entrySet()) result = result.plus(entry.getValue().atLevel(levels.applyAsInt(entry.getKey())).scaled(multipliers.applyAsDouble(entry.getKey())));
        return result;
    }
    public static PopulationSettings load(YamlConfiguration config, YamlConfiguration catalog) {
        int maximum = integer(config,"simulation.maximum-population",1,1000000);
        int initial = integer(config,"simulation.initial-population",0,maximum);
        var rules = new PopulationMath.Rules(maximum,
                number(config,"simulation.growth-rate",0,1), number(config,"simulation.decline-rate",0,1),
                number(config,"simulation.workforce-share",0,1), number(config,"simulation.food-per-person",0.001,1000),
                number(config,"simulation.water-per-person",0.001,1000), number(config,"simulation.critical-coverage",0,1),
                number(config,"simulation.growth-happiness",0,100), number(config,"simulation.decline-happiness",0,100),
                number(config,"happiness.base",0,100), number(config,"happiness.unemployment-penalty",0,100),
                number(config,"happiness.housing-penalty",0,100), number(config,"happiness.food-penalty",0,100),
                number(config,"happiness.water-penalty",0,100));
        if (rules.declineHappiness() > rules.growthHappiness()) throw new IllegalArgumentException("Порог убыли выше порога роста");
        Map<String, Building> buildings = new LinkedHashMap<>();
        var section = catalog.getConfigurationSection("buildings");
        if (section == null) throw new IllegalArgumentException("Нет раздела buildings");
        for (String id : section.getKeys(false)) {
            if (!id.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Некорректный ID здания: " + id);
            var s = section.getConfigurationSection(id);
            if (s == null) throw new IllegalArgumentException("Некорректное здание: " + id);
            int max = integer(s,"maximum-level",1,100);
            buildings.put(id, new Building(s.getString("name",id), s.getString("icon","BRICKS"),
                    integer(s,"minimum-level",1,max), max, readCapacity(s, false)));
        }
        var b = config.getConfigurationSection("base");
        if (b == null) throw new IllegalArgumentException("Нет раздела base");
        return new PopulationSettings(integer(config,"simulation.interval-seconds",10,604800)*1000L,
                initial, readCapacity(b, true), rules, buildings);
    }
    private static Capacity readCapacity(ConfigurationSection c, boolean base) {
        return new Capacity(optionalInt(c,"housing"), optionalInt(c,"jobs"),
                optional(c,"food",0,10000000), optional(c,"water",0,10000000),
                base ? 0 : optional(c,"happiness",-100,100));
    }
    private static int optionalInt(ConfigurationSection c, String key) { return c.contains(key) ? integer(c,key,0,10000000) : 0; }
    private static double optional(ConfigurationSection c, String key, double min, double max) { return c.contains(key) ? number(c,key,min,max) : 0; }
    private static int integer(ConfigurationSection c, String key, int min, int max) {
        double value = number(c,key,min,max);
        if (value != Math.rint(value)) throw new IllegalArgumentException(key + ": требуется целое число");
        return (int)value;
    }
    private static double number(ConfigurationSection c, String key, double min, double max) {
        Object raw = c.get(key);
        if (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue()<min || n.doubleValue()>max)
            throw new IllegalArgumentException(c.getCurrentPath()+"."+key+": требуется число " + min + ".." + max);
        return n.doubleValue();
    }
}
