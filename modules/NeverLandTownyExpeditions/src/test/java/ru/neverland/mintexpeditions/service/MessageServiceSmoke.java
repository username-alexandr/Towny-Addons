package ru.neverland.mintexpeditions.service;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;

public final class MessageServiceSmoke {
    public static void main(String[] args) throws Exception {
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
