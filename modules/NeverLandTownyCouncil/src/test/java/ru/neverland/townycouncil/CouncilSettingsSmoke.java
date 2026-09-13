package ru.neverland.townycouncil;

import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

public final class CouncilSettingsSmoke {
    public static void main(String[] args) throws Exception {
        var y = new YamlConfiguration();
        try (var in = CouncilSettingsSmoke.class.getResourceAsStream("/config.yml")) {
            y.loadFromString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        var settings = CouncilSettings.load(y);
        for (var role : MinisterRole.values()) {
            var nodes = settings.permissions().get(role);
            CouncilRepositorySmoke.check(nodes.contains(role.permission()), "role marker " + role);
            CouncilRepositorySmoke.check(nodes.stream().noneMatch(n -> n.contains("admin") || n.contains("bypass") || n.contains("*")), "no administrative defaults");
            for (var other : MinisterRole.values()) if (other != role)
                CouncilRepositorySmoke.check(Collections.disjoint(nodes, settings.permissions().get(other)), "distinct permissions " + role);
        }
        y.set("roles.economy.permissions", List.of("external.bank.statement"));
        CouncilRepositorySmoke.check(CouncilSettings.load(y).permissions().get(MinisterRole.ECONOMY).contains("external.bank.statement"), "custom permission");
        y.set("roles.economy.permissions", List.of("neverlandtownycouncil.admin"));
        CouncilRepositorySmoke.reject(() -> CouncilSettings.load(y), "minister can appoint all ministers");
        y.set("roles.economy.permissions", List.of("external.*"));
        CouncilRepositorySmoke.reject(() -> CouncilSettings.load(y), "wildcard accepted");
        y.set("roles.economy.permissions", List.of(12));
        CouncilRepositorySmoke.reject(() -> CouncilSettings.load(y), "invalid scalar silently skipped");
        y.set("roles.economy", null);
        CouncilRepositorySmoke.reject(() -> CouncilSettings.load(y), "missing role silently defaulted");
        System.out.println("CouncilSettingsSmoke PASS: distinct roles, explicit custom permissions and strict validation");
    }
}
