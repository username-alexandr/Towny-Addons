package ru.neverland.townytreasury.service;

import org.bukkit.command.CommandSender;

/** One permission gate for menu visibility, execution, help and completion. */
public final class TreasuryAccess {
    public static final String EXPORT = "neverlandtownytreasury.export";
    private TreasuryAccess() { }
    public static boolean canExport(CommandSender sender) { return sender.hasPermission(EXPORT); }
}
