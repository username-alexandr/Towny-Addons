package ru.neverland.townycouncil;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;

public record CouncilSettings(Map<MinisterRole, Set<String>> permissions) {
    public CouncilSettings {
        var copy = new EnumMap<MinisterRole, Set<String>>(MinisterRole.class);
        for (var role : MinisterRole.values()) {
            var nodes = new TreeSet<>(Objects.requireNonNull(permissions.get(role), "Нет настроек " + role.id()));
            nodes.add(role.permission());
            for (String node : nodes) if (!node.matches("[a-z0-9_]+(?:[.][a-z0-9_-]+)+"))
                throw new IllegalArgumentException("Некорректное permission: " + node);
            // Appointment/admin controls must never be inherited from a ministry.
            if (nodes.contains("neverlandtownycouncil.admin") || nodes.contains("neverlandtownycouncil.manage"))
                throw new IllegalArgumentException("Министерство не может выдавать права назначения министров");
            copy.put(role, Set.copyOf(nodes));
        }
        permissions = Collections.unmodifiableMap(copy);
    }
    public static CouncilSettings load(ConfigurationSection yaml) {
        var roles = yaml.getConfigurationSection("roles");
        if (roles == null || !roles.getKeys(false).equals(Set.of("economy", "defense", "construction", "foreign")))
            throw new IllegalArgumentException("Ожидались четыре раздела roles: economy, defense, construction, foreign");
        var result = new EnumMap<MinisterRole, Set<String>>(MinisterRole.class);
        for (var role : MinisterRole.values()) {
            Object raw = roles.get(role.id() + ".permissions");
            if (!(raw instanceof List<?> list) || list.stream().anyMatch(v -> !(v instanceof String)))
                throw new IllegalArgumentException("Ожидался список permissions: " + role.id());
            result.put(role, new HashSet<>(roles.getStringList(role.id() + ".permissions")));
        }
        return new CouncilSettings(result);
    }
}
