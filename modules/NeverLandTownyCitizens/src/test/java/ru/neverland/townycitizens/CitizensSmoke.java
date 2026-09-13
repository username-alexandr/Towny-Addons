package ru.neverland.townycitizens;

import java.nio.file.Files;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townycitizens.model.*;

public final class CitizensSmoke {
    public static void main(String[] args) throws Exception {
        UUID town = UUID.randomUUID(), player = UUID.randomUUID();
        var temporary = new CitizenshipRecord(town, player, CitizenshipStatus.TEMPORARY, 1000, 100, "mayor", "test");
        check(temporary.effective(true, 999) == CitizenshipStatus.TEMPORARY, "before expiry");
        check(temporary.effective(true, 1000) == CitizenshipStatus.FOREIGNER, "exact expiry");
        check(temporary.effective(false, 500) == CitizenshipStatus.FOREIGNER, "membership lost");
        var honorary = new CitizenshipRecord(town, player, CitizenshipStatus.HONORARY, 0, 100, "mayor", "test");
        check(honorary.effective(false, 500) == CitizenshipStatus.HONORARY, "honorary visitor");
        try { new CitizenshipRecord(town, player, CitizenshipStatus.TEMPORARY, 0, 0, "mayor", "test"); throw new AssertionError("missing expiry accepted"); }
        catch (IllegalArgumentException expected) { }
        var path = Files.createTempDirectory("citizens-test-").resolve("citizens.yml");
        var repository = new CitizensRepository(path); repository.load(); repository.put(temporary);
        var reload = new CitizensRepository(path); reload.load(); check(reload.get(town, player).equals(temporary), "restart");
        check(reload.get(UUID.randomUUID(), player) == null, "town isolation");
        Files.delete(path); Files.createDirectory(path); Files.writeString(path.resolve("obstruction"), "keep");
        try { reload.put(honorary); throw new AssertionError("write failure ignored"); } catch (java.io.IOException expected) { }
        check(reload.get(town, player).equals(temporary), "failed write did not change memory");
        check(!reload.writable(), "fail closed");
        Files.delete(path.resolve("obstruction")); Files.delete(path);
        try { reload.put(honorary); throw new AssertionError("automatic retry after fault"); } catch (java.io.IOException expected) { }
        Files.writeString(path, "schema: 1\nrecords:\n  broken:\n    status: HONORARY\n");
        try { new CitizensRepository(path).load(); throw new AssertionError("corrupt record skipped"); } catch (Exception expected) { }
        String config;
        try (var stream = CitizensSmoke.class.getResourceAsStream("/config.yml")) { config = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        var y = new YamlConfiguration(); y.loadFromString(config); var settings = CitizensSettings.load(y);
        check(settings.policies().get(CitizenshipStatus.CITIZEN).rights().contains(CitizenRight.VOTE), "citizen franchise");
        check(!settings.policies().get(CitizenshipStatus.TEMPORARY).rights().contains(CitizenRight.VOTE), "temporary franchise");
        check(settings.policies().get(CitizenshipStatus.HONORARY).tax() == .5, "honorary tax");
        y.set("statuses.citizen.tax-multiplier", Double.NaN);
        try { CitizensSettings.load(y); throw new AssertionError("NaN tax accepted"); } catch (IllegalArgumentException expected) { }
        System.out.println("CitizensSmoke: 16 status, persistence and policy checks passed");
    }
    private static void check(boolean condition, String name) { if (!condition) throw new AssertionError(name); }
}
