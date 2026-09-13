package ru.neverland.townytreasury.service;

import org.bukkit.command.CommandSender;

/** One permission gate for menu visibility, execution, help and completion. */
public final class TreasuryAccess {
    public static final String EXPORT = "neverlandtownytreasury.export";
    private TreasuryAccess() { }
    public static boolean canExport(CommandSender sender) {
        if (sender.hasPermission(EXPORT)) return true;
        if (!(sender instanceof org.bukkit.entity.Player player)) return false;
        var resident = com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(player);
        var town = resident == null ? null : resident.getTownOrNull();
        return town != null && ru.neverland.core.CouncilAccess.allows(player, town.getUUID(), "export");
    }
}
