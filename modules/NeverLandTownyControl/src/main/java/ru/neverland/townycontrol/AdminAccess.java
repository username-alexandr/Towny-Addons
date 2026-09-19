package ru.neverland.townycontrol;

import org.bukkit.command.CommandSender;

final class AdminAccess {
    static final String ADMIN="neverlandtownycontrol.admin", AUDIT="neverlandtownycontrol.audit", EXPORT="neverlandtownycontrol.audit.export";
    private AdminAccess() { }
    static boolean has(CommandSender sender,String permission) { return sender.isOp() || sender.hasPermission(permission); }
    static boolean admin(CommandSender sender) { return has(sender,ADMIN); }
}
