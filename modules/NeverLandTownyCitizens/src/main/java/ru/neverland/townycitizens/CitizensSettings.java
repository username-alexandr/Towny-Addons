package ru.neverland.townycitizens;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import ru.neverland.townycitizens.model.CitizenRight;
import ru.neverland.townycitizens.model.CitizenshipStatus;

public record CitizensSettings(Map<CitizenshipStatus, Policy> policies, int maximumTemporaryDays) {
    public record Policy(double tax, Set<CitizenRight> rights) {
        public Policy { rights = Set.copyOf(rights); if (!Double.isFinite(tax) || tax < 0 || tax > 10) throw new IllegalArgumentException("Множитель налога: от 0 до 10"); }
    }
    public CitizensSettings { policies = Map.copyOf(policies); }
    public static CitizensSettings load(ConfigurationSection config) {
        var policies = new EnumMap<CitizenshipStatus, Policy>(CitizenshipStatus.class);
        for (var status : CitizenshipStatus.values()) {
            String key = "statuses." + status.name().toLowerCase(java.util.Locale.ROOT);
            Object raw = config.get(key + ".tax-multiplier");
            if (!(raw instanceof Number n) || !config.isList(key + ".rights")) throw new IllegalArgumentException("Не задана политика " + key);
            Set<CitizenRight> rights = EnumSet.noneOf(CitizenRight.class);
            for (Object right : config.getList(key + ".rights")) {
                if (!(right instanceof String text)) throw new IllegalArgumentException("Право должно быть строкой");
                rights.add(CitizenRight.valueOf(text));
            }
            policies.put(status, new Policy(n.doubleValue(), rights));
        }
        Object days = config.get("temporary.maximum-days");
        if (!(days instanceof Integer n) || n < 1 || n > 3650) throw new IllegalArgumentException("temporary.maximum-days: 1–3650");
        return new CitizensSettings(policies, n);
    }
}
