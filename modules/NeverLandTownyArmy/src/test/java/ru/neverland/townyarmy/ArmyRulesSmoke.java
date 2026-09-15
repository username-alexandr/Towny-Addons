package ru.neverland.townyarmy;
import java.util.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import static ru.neverland.townyarmy.ArmyModel.*;
public final class ArmyRulesSmoke {
    static int assertions;
    static void check(boolean b) { assertions++; if (!b) throw new AssertionError("check " + assertions); }
    static ArmySettings settings() throws Exception { try (var in = ArmyRulesSmoke.class.getResourceAsStream("/config.yml")) { return ArmySettings.load(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8))); } }
    static Soldier soldier(UUID town, Unit unit, Rank rank, int train, long minutes) { return new Soldier(UUID.randomUUID(), town, unit, rank, Status.ACTIVE, true, train, 100, minutes * 60000, 1, 0, 0, 0, 0); }
    public static void main(String[] args) throws Exception {
        var settings = settings(); var town = UUID.randomUUID(); var levels = Map.of("army", 3, "barracks", 1, "stables", 1, "shipyard", 1, "workshop", 1, "observatory", 1);
        var roster = new ArrayList<Soldier>(); for (Unit unit : Unit.values()) for (int i = 0; i < 12; i++) roster.add(soldier(town, unit, Rank.PRIVATE, 100, 0));
        var valid = new HashSet<>(roster.stream().map(Soldier::resident).toList());
        var r = ArmyRules.calculate(roster, valid, Set.of(), levels, true, settings);
        check(r.active().size() == 21); check(r.count().equals(Map.of(Unit.INFANTRY,10,Unit.CAVALRY,5,Unit.NAVY,4,Unit.AIR,2))); check(r.readiness() == 100); check(r.defense() > 0);
        Collections.reverse(roster); check(ArmyRules.calculate(roster, valid, Set.of(), levels, true, settings).equals(r));
        var shortage = ArmyRules.calculate(roster, valid, Set.of(), levels, false, settings); check(Math.abs(shortage.score() * 4 - r.score()) < .0001); check(shortage.readiness() == 25);
        check(ArmyRules.calculate(roster, Set.of(), Set.of(), levels, true, settings).active().isEmpty());
        check(ArmyRules.capacity(Unit.AIR, Map.of("army", 3, "workshop", 3), settings) == 0);
        check(ArmyRules.calculate(roster, valid, Set.of(), Map.of("army", 1, "barracks", 5, "stables", 5), true, settings).active().size() == 10);
        var reserve = roster.get(0).status(Status.RESERVE); check(ArmyRules.calculate(List.of(reserve), Set.of(reserve.resident()), Set.of(), levels, true, settings).active().isEmpty());
        check(ArmyRules.calculate(List.of(reserve), Set.of(reserve.resident()), Set.of(reserve.resident()), levels, true, settings).active().size() == 1);
        check(ArmyRules.calculate(List.of(reserve.equipment(0)), Set.of(reserve.resident()), Set.of(reserve.resident()), levels, true, settings).score() == 0);
        var actor = soldier(town, Unit.INFANTRY, Rank.CAPTAIN, 100, 1000); var target = soldier(town, Unit.INFANTRY, Rank.PRIVATE, 10, 10);
        check(ArmyRules.canPromote(actor, target, Rank.CORPORAL, false, settings));
        check(!ArmyRules.canPromote(target, target, Rank.CORPORAL, false, settings));
        check(!ArmyRules.canPromote(soldier(UUID.randomUUID(), Unit.INFANTRY, Rank.GENERAL, 100, 1000), target, Rank.CORPORAL, false, settings));
        check(!ArmyRules.canPromote(actor, target, Rank.SERGEANT, true, settings));
        check(!ArmyRules.canPromote(actor, soldier(town,Unit.INFANTRY,Rank.PRIVATE,9,10), Rank.CORPORAL, true, settings));
        check(!ArmyRules.canPromote(actor, soldier(town,Unit.INFANTRY,Rank.PRIVATE,10,9), Rank.CORPORAL, true, settings));
        check(!ArmyRules.canPromote(actor, soldier(town,Unit.INFANTRY,Rank.COLONEL,100,1000), Rank.GENERAL, true, settings));
        check(!ArmyRules.canPromote(actor, soldier(town,Unit.INFANTRY,Rank.LIEUTENANT,100,1000), Rank.CAPTAIN, false, settings));
        check(ArmyRules.trainingComplete(60000, 12, true, true, settings));
        check(!ArmyRules.trainingComplete(59999, 12, true, true, settings)); check(!ArmyRules.trainingComplete(60000, 11, true, true, settings));
        check(!ArmyRules.trainingComplete(60000, 12, false, true, settings)); check(!ArmyRules.trainingComplete(60000, 12, true, false, settings));
        check(!ArmyRules.trainingComplete(60000, Double.NaN, true, true, settings));
        var warning = target.warning(false, 1).warning(false, 2).warning(false, 3); check(warning.status() == Status.RESERVE && warning.warnings() == 3 && warning.suspendedUntil() == 86400003);
        check(warning.warning(true, 4).warnings() == 0); check(target.unit(Unit.NAVY).equipment() == 0);
        check(ArmyRules.upkeep(List.of(target), Set.of(target.resident()), settings).equals(Map.of("food",1000L,"water",1000L)));
        System.out.println("Army rules: " + assertions + " capacity, readiness, rank, training and discipline assertions PASS");
    }
}
