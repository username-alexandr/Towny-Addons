package ru.neverland.townybuilds.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.townybuilds.NeverLandTownyBuilds;
import ru.neverland.townybuilds.service.MessageService;
import ru.neverland.townybuilds.civic.CivicService;
import org.bukkit.entity.Player;

import java.util.List;

public final class AdminCommand implements CommandExecutor, TabCompleter {
    private final NeverLandTownyBuilds plugin;
    private final MessageService messages;
    private final CivicService civic;

    public AdminCommand(NeverLandTownyBuilds plugin, MessageService messages, CivicService civic) {
        this.plugin = plugin;
        this.messages = messages;
        this.civic = civic;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("neverlandtownybuilds.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if(args.length>0&&args[0].equalsIgnoreCase("shop")){civic.shops().admin(sender,args);return true;}
        if(args.length==1&&args[0].equalsIgnoreCase("diagnostics")){ru.neverland.core.Diagnostics.show(sender);return true;}
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPlugin();
            messages.send(sender, "reload-success");
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("stall")) {
            if (args[1].equalsIgnoreCase("list")) {
                sender.sendMessage("Торговые места: " + String.join(", ", civic.stallIds()));
                return true;
            }
            if (args.length >= 3 && args[1].equalsIgnoreCase("remove")) {
                civic.removeStall(sender, args[2]);
                return true;
            }
            if (args.length >= 3 && args[1].equalsIgnoreCase("set") && sender instanceof Player player) {
                civic.setStall(player, args[2]);
                return true;
            }
        }
        sender.sendMessage("/townybuilds reload | /townybuilds stall <set|remove|list> [id] | /townybuilds shop payments | /townybuilds diagnostics");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return List.of("reload", "stall", "shop", "diagnostics");
        if (args.length == 2 && args[0].equalsIgnoreCase("stall")) return List.of("set", "remove", "list");
        if (args.length == 3 && args[0].equalsIgnoreCase("stall") && args[1].equalsIgnoreCase("remove")) {
            return civic.stallIds();
        }
        return List.of();
    }
}
