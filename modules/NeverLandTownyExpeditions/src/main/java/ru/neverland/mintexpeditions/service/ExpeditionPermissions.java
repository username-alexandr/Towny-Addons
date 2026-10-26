package ru.neverland.mintexpeditions.service;
import org.bukkit.permissions.Permissible;
public final class ExpeditionPermissions {
 private ExpeditionPermissions() {}
 public static boolean canReturn(Permissible player) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return player.hasPermission("neverlandtownyexpeditions.return"); } }
 public static boolean shieldDenied(Permissible player, boolean inExpedition) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
  return inExpedition && !player.hasPermission("neverlandtownyexpeditions.shield");
 } }
}
