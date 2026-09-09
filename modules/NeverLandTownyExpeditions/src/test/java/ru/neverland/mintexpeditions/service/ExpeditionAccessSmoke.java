package ru.neverland.mintexpeditions.service;
import java.util.*;
import java.nio.file.*;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;
import org.bukkit.permissions.Permissible;
public final class ExpeditionAccessSmoke {
 public static void main(String[] args) throws Exception {
  Set<String> permissions = new HashSet<>();
  Permissible player = (Permissible) Proxy.newProxyInstance(Permissible.class.getClassLoader(), new Class[]{Permissible.class},
          (object, method, values) -> method.getName().equals("hasPermission") && permissions.contains(values[0]));
  check(!ExpeditionPermissions.canReturn(player), "ordinary player no return");
  check(ExpeditionPermissions.shieldDenied(player, true), "shield denied in expedition");
  check(!ExpeditionPermissions.shieldDenied(player, false), "normal world shield unchanged");
  permissions.add("neverlandtownyexpeditions.return");
  check(ExpeditionPermissions.canReturn(player) && ExpeditionPermissions.shieldDenied(player, true), "independent rights");
  permissions.add("neverlandtownyexpeditions.shield");
  check(!ExpeditionPermissions.shieldDenied(player, true), "shield allowed by permission");
  permissions.clear(); check(!ExpeditionPermissions.canReturn(player), "revoked menu permission checked again");
  Path temp = Files.createTempDirectory("return-tickets-");
  try {
   var file = temp.resolve("tickets.yml").toFile(); var logger = Logger.getAnonymousLogger();
   UUID leader = UUID.randomUUID(), participant = UUID.randomUUID();
   ReturnTickets tickets = new ReturnTickets(file, logger, 0);
   tickets.grant(List.of(leader, participant), leader, 1000);
   check(tickets.get(participant, 999).campOwner().equals(leader), "participant returns to expedition camp");
   tickets = new ReturnTickets(file, logger, 500);
   check(tickets.get(participant, 500) != null, "restart preserves manual return");
   tickets.remove(participant); tickets = new ReturnTickets(file, logger, 500);
   check(tickets.get(participant, 500) == null && tickets.get(leader, 500) != null, "one-time claim is per player");
   check(tickets.get(leader, 1000) == null, "expiry boundary");
   tickets.prune(1000); check(new ReturnTickets(file, logger, 1000).get(leader, 1000) == null, "expired ticket not resurrected");
  } finally { try (var paths = Files.walk(temp)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.delete(path); } catch(Exception ignored) {} }); } }
  System.out.println("ExpeditionAccessSmoke OK: permissions, revocation, ticket restart/expiry");
 }
 private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
