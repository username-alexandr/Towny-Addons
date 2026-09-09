package ru.neverland.mintevents;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.mintevents.model.*;
import ru.neverland.mintevents.service.*;
public final class RaidWavesSmoke {
 public static void main(String[] args) throws Exception {
  YamlConfiguration yaml;
  try(var input = new InputStreamReader(Objects.requireNonNull(RaidWavesSmoke.class.getResourceAsStream("/raids.yml")), StandardCharsets.UTF_8)) { yaml = YamlConfiguration.loadConfiguration(input); }
  RaidCatalog catalog = new RaidCatalog(yaml);
  ActiveEvent event = new ActiveEvent(UUID.randomUUID(), "raid", 0, 100000, 0, 100, 0, 0);
  event.raid(new RaidState()); event.addProgress(1000);
  check(!event.completed(), "supplies must not bypass waves");
  double priorThreat = 0, priorRatio = 0;
  int kills = 0;
  for (int wave = 1; wave <= 10; wave++) {
   var definition = catalog.wave(wave); var state = event.raid(); state.begin(definition.mobs());
   check(state.wave() == wave && !state.clear(), "wave order");
   double threat = definition.mobs().stream().map(catalog::mob).mapToDouble(m -> m.health()*definition.healthMultiplier()*m.damage()*definition.damageMultiplier()).sum();
   check(threat > priorThreat, "difficulty must rise: " + wave); priorThreat = threat;
   List<UUID> entities = new ArrayList<>();
   while (state.next() != null) {
    String id = state.next(); UUID entity = UUID.randomUUID(); state.spawned(entity); entities.add(entity);
    check(catalog.mob(id).points() >= 1 && catalog.mob(id).points() < 10, "per-mob points below old 10");
   }
   check(!state.clear() && !event.completed(), "alive mobs cannot be skipped");
   try { state.begin(List.of("zombie")); throw new AssertionError("overlapping waves"); } catch (IllegalStateException expected) { }
   // Killing half a wave, then restart: only unfinished enemies return, with a different marker.
   if (wave == 5) {
    UUID killed = entities.remove(0); check(state.died(killed) != null && state.died(killed) == null, "exactly once"); kills++;
    YamlConfiguration saved = new YamlConfiguration(); state.save(saved);
    var restored = RaidState.load(saved); check(!restored.generation().equals(state.generation()), "old bodies invalidated");
    check(restored.wave() == 5 && restored.remaining() == entities.size() && restored.aliveCount() == 0, "resume remaining enemies");
    event.raid(restored); state = restored; entities.clear();
    while (state.next() != null) { UUID entity = UUID.randomUUID(); state.spawned(entity); entities.add(entity); }
   }
   for (UUID entity : entities) {
    check(RaidKillCredit.points(event, EventMode.RAID, true, 2, 100) == 2, "points above supplies goal still credited");
    check(RaidKillCredit.points(event, EventMode.RAID, false, 2, 100) == 0, "environment earns no points");
    check(state.died(entity) != null && state.died(entity) == null, "duplicate death"); kills++;
    check(state.ratio() >= priorRatio, "monotonic progress"); priorRatio = state.ratio();
   }
   check(event.completed() == (wave == 10), "victory only after 10 waves");
  }
  check(event.progressRatio() == 1 && event.raid().wave() == 10, "finished state");
  try { event.raid().begin(List.of("zombie")); throw new AssertionError("eleventh wave"); } catch (IllegalStateException expected) { }
  YamlConfiguration legacy = new YamlConfiguration(); legacy.set("prefix", "&d[MintTownyEvents] "); legacy.set("custom", "Свой текст");
  check(MessageService.migrateLegacy(legacy, new YamlConfiguration()), "old prefix migration");
  check(!legacy.getString("prefix").contains("MintTownyEvents") && legacy.getString("custom").equals("Свой текст"), "preserve custom messages");
  check(!MessageService.migrateLegacy(legacy, new YamlConfiguration()), "idempotent prefix");
  System.out.println("RaidWavesSmoke OK: 10 growing waves, " + kills + " kills, restart and duplicate protection");
 }
 private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
