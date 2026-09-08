package ru.neverland.mintevents;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.mintevents.model.ActiveEvent;
import ru.neverland.mintevents.model.EventMode;
import ru.neverland.mintevents.service.MessageService;
import ru.neverland.mintevents.service.RaidKillCredit;

public final class RaidKillAndMessagesSmoke {
    public static void main(String[] args) throws Exception {
        ActiveEvent raid = new ActiveEvent(UUID.randomUUID(), "raid", 0, 10000, 0, 460, 0, 0);
        int points = RaidKillCredit.points(raid, EventMode.RAID, true, 10, 100);
        check(points == 10 && raid.addProgress(points) == 10, "player kill must advance 0/460");
        check(RaidKillCredit.points(raid, EventMode.RAID, false, 10, 100) == 0, "environment death");
        check(RaidKillCredit.points(raid, EventMode.DROUGHT, true, 10, 100) == 0, "different event");
        check(RaidKillCredit.points(null, EventMode.RAID, true, 10, 100) == 0, "no active event");
        check(RaidKillCredit.points(raid, EventMode.RAID, true, 10, 10000) == 0, "expired event");
        check(RaidKillCredit.points(raid, EventMode.RAID, true, 0, 100) == 0, "disabled credit");
        raid.addProgress(449);
        check(RaidKillCredit.points(raid, EventMode.RAID, true, 10, 100) == 1, "cap at remaining goal");
        raid.addProgress(1);
        check(raid.completed(), "last kill completes raid");
        check(RaidKillCredit.points(raid, EventMode.RAID, true, 10, 100) == 0, "completed event");

        YamlConfiguration old = new YamlConfiguration();
        old.set("prefix", "Custom ");
        try (var in = java.util.Objects.requireNonNull(RaidKillAndMessagesSmoke.class.getResourceAsStream("/messages.yml"));
             var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            old.setDefaults(YamlConfiguration.loadConfiguration(reader));
        }
        String wave = MessageService.template(old, "raid-wave-town");
        check(wave.contains("%count%") && !wave.equals("raid-wave-town"), "bundled wave template");
        check(MessageService.template(old, "raid-kill-progress").contains("%points%"), "bundled kill template");
        old.set("raid-wave-town", "Custom wave %count%");
        check(MessageService.template(old, "raid-wave-town").equals("Custom wave %count%"), "preserve custom text");
        check(MessageService.template(old, "unknown").equals("unknown"), "unknown key fallback");
        System.out.println("RaidKillAndMessagesSmoke OK");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
