package ru.neverland.mintexpeditions.service;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;

public final class MessageServiceSmoke {
    public static void main(String[] args) throws Exception {
        var legacyCosts = java.util.List.of("GOLDEN_CARROT:24", "POTION:4", "OAK_BOAT:2");
        var migrated = PotionSupplies.migrate("royal_galleon", legacyCosts);
        check(migrated.contains("POTION:WATER_BREATHING:2") && migrated.contains("POTION:NIGHT_VISION:2"), "both explicit potions");
        check(!migrated.contains("POTION:4") && migrated.size() == 4, "remove ambiguous cost only");
        check(PotionSupplies.migrate("royal_galleon", migrated).equals(migrated), "idempotent migration");
        check(PotionSupplies.migrate("other", legacyCosts).equals(legacyCosts), "other expeditions unchanged");
        check(PotionSupplies.migrate("drowned_temple", java.util.List.of("POTION:2")).size() == 2, "temple has two potion types");
        check(PotionSupplies.name(org.bukkit.potion.PotionType.WATER_BREATHING).contains("дыхания"), "breathing label");
        check(PotionSupplies.name(org.bukkit.potion.PotionType.NIGHT_VISION).contains("ночного зрения"), "vision label");
        YamlConfiguration oldConfig = new YamlConfiguration();
        oldConfig.loadFromString("prefix: 'Custom prefix '\nstarted: 'Custom start'\n");
        try (var input = Objects.requireNonNull(MessageServiceSmoke.class.getResourceAsStream("/messages.yml"));
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            oldConfig.setDefaults(YamlConfiguration.loadConfiguration(reader));
        }
        var coordinates = Map.of("world", "world", "x", -123, "y", 65, "z", 456, "distance", "100 блоков");
        String message = MessageService.render(oldConfig, "target-coordinates", coordinates);
        check(message.contains("X: -123, Y: 65, Z: 456"), "old config must use bundled coordinates");
        check(!message.contains("%"), "coordinate placeholders must be resolved");
        check(MessageService.template(oldConfig, "started").equals("Custom start"), "keep custom messages");
        oldConfig.set("target-coordinates", "%world%: %x%/%y%/%z%");
        check(MessageService.render(oldConfig, "target-coordinates", coordinates).equals("world: -123/65/456"), "keep custom coordinate format");
        check(MessageService.template(oldConfig, "missing-key").equals("missing-key"), "unknown key fallback");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
