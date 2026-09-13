package ru.neverland.core;

import java.util.UUID;
import org.bukkit.entity.Player;

/** Permission plus live, town-scoped appointment. Never substitute a minister for the mayor. */
public final class CouncilAccess {
    private CouncilAccess() { }
    public static boolean allows(Player player, UUID town, String action) {
        if (player == null || town == null) return false;
        String permission = "neverlandtownycouncil.action." + action;
        if (!player.hasPermission(permission)) return false;
        var c = ApiServices.connect("NeverLandTownyCouncil", "ru.neverland.townycouncil.api.TownyCouncilApi", 1, "allows");
        if (!c.ready()) return false;
        try { return Boolean.TRUE.equals(c.invoke("allows", new Class<?>[]{UUID.class, UUID.class, String.class}, town, player.getUniqueId(), permission)); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ex) { return false; }
    }
}
