package ru.neverland.mintexpeditions.service;
import org.bukkit.permissions.Permissible;
public final class ExpeditionPermissions {
 private ExpeditionPermissions() {}
 public static boolean canReturn(Permissible player) { synchronized (ru.neverland.core.SuiteState.lock()) { return player.hasPermission("neverlandtownyexpeditions.return"); } }
 public static boolean shieldDenied(Permissible player, boolean inExpedition) { synchronized (ru.neverland.core.SuiteState.lock()) {
  return inExpedition && !player.hasPermission("neverlandtownyexpeditions.shield");
 } }
}
