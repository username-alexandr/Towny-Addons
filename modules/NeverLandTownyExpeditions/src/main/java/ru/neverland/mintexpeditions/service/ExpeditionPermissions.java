package ru.neverland.mintexpeditions.service;
import org.bukkit.permissions.Permissible;
public final class ExpeditionPermissions {
 private ExpeditionPermissions() {}
 public static boolean canReturn(Permissible player) { return player.hasPermission("neverlandtownyexpeditions.return"); }
 public static boolean shieldDenied(Permissible player, boolean inExpedition) {
  return inExpedition && !player.hasPermission("neverlandtownyexpeditions.shield");
 }
}
