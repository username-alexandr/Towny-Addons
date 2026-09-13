package ru.neverland.townycouncil;

import java.nio.file.*;
import java.util.*;

public final class CouncilRepositorySmoke {
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
    interface Work { void run() throws Exception; }
    static void reject(Work work, String label) throws Exception { try { work.run(); } catch (Exception expected) { return; } throw new AssertionError(label); }
    public static void main(String[] args) throws Exception {
        UUID t = UUID.randomUUID(), r = UUID.randomUUID(), m = UUID.randomUUID();
        var a = new Appointment(t, MinisterRole.ECONOMY, r, m, 100, "mayor");
        Path file = Files.createTempDirectory("council-").resolve("council.yml");
        var store = new CouncilRepository(file); store.load();
        var audit = new CouncilRepository.Audit(t, 100, "mayor", "APPOINT economy");
        store.commit(Map.of(a.key(), a), List.of(audit));
        var loaded = new CouncilRepository(file); loaded.load();
        check(loaded.all().equals(store.all()) && loaded.history().equals(List.of(audit)), "restart appointments and audit");
        reject(() -> loaded.all().clear(), "mutable snapshot");
        var second = new Appointment(t, MinisterRole.DEFENSE, r, m, 101, "mayor");
        reject(() -> loaded.commit(Map.of(a.key(), a, second.key(), second), List.of()), "multiple ministries");
        check(loaded.writable() && loaded.all().size() == 1, "validation failure preserves registry");
        Files.delete(file); Files.createDirectory(file); Files.writeString(file.resolve("obstruction"), "preserve");
        reject(() -> loaded.commit(Map.of(), List.of(audit)), "failed dismissal ignored");
        check(!loaded.writable() && loaded.all().get(a.key()).equals(a), "failed write keeps previous memory and locks registry");
        Files.delete(file.resolve("obstruction")); Files.delete(file);
        reject(() -> loaded.commit(Map.of(), List.of()), "automatic retry after disk error");
        Files.writeString(file, "schema: 1\nappointments:\n  invalid:\n    role: economy\nhistory: {}\n");
        reject(() -> new CouncilRepository(file).load(), "corrupt assignment skipped");
        Files.writeString(file, "schema: 2\nappointments: {}\nhistory: {}\n");
        reject(() -> new CouncilRepository(file).load(), "unknown schema accepted");
        reject(() -> new Appointment(t, MinisterRole.DEFENSE, m, m, 0, "mayor"), "mayor appointed minister");
        System.out.println("CouncilRepositorySmoke PASS: durable registry, audit, uniqueness, corruption and write faults");
    }
}
