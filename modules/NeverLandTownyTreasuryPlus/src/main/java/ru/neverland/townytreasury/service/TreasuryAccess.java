package ru.neverland.townytreasury.service;

import org.bukkit.command.CommandSender;

/** One permission gate for menu visibility, execution, help and completion. */
public final class TreasuryAccess {
    public static final String EXPORT = "neverlandtownytreasury.export";
    private TreasuryAccess() { }
    public static boolean canExport(CommandSender sender) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        if (!(sender instanceof org.bukkit.entity.Player)) return sender.hasPermission(EXPORT);
        if (!(sender instanceof org.bukkit.entity.Player player)) return false;
        var resident = com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(player);
        var town = resident == null ? null : resident.getTownOrNull();
        return town==null?sender.hasPermission(EXPORT):town.isMayor(resident)||ru.neverland.core.CouncilAccess.check(player,town.getUUID(),"export",sender.hasPermission(EXPORT));
    } }
}
