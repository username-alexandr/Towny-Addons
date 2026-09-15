package ru.neverland.townyarmy;
import java.util.*;
import static ru.neverland.townyarmy.ArmyModel.*;
/** Persist imported roster before retiring its former owner; replay only the acknowledgement. */
public final class ArmyMigration {
    public interface Legacy { Map<UUID, UUID> legacy() throws Exception; void handoff(Map<UUID, UUID> expected) throws Exception; }
    private ArmyMigration() { }
    public static void resume(ArmyRepository repository, Legacy legacy, long now) throws Exception {
        if (!repository.state().imported()) {
            var roster = legacy.legacy(); var d = repository.draft(); d.imported = true; d.legacy.putAll(roster);
            for (var e : roster.entrySet()) d.soldier(Soldier.applicant(e.getKey(), e.getValue(), Unit.INFANTRY, now).swear().status(Status.ACTIVE));
            repository.commit(d, SYSTEM, SYSTEM, SYSTEM, "MIGRATION", "Перенос личного состава из Builds", now);
        }
        if (!repository.state().handoff()) {
            legacy.handoff(repository.state().legacy()); var d = repository.draft(); d.handoff = true;
            repository.commit(d, SYSTEM, SYSTEM, SYSTEM, "HANDOFF", "Builds подтвердил передачу состава", now);
        }
    }
}
