package ru.neverland.townybuilds.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.townybuilds.NeverLandTownyBuilds;
import ru.neverland.townybuilds.service.MessageService;

import java.util.List;

public final class AdminCommand implements CommandExecutor, TabCompleter {
    private final NeverLandTownyBuilds plugin;
    private final MessageService messages;

    public AdminCommand(NeverLandTownyBuilds plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("neverlandtownybuilds.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPlugin();
            messages.send(sender, "reload-success");
            return true;
        }
        sender.sendMessage("/townybuilds reload");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? List.of("reload") : List.of();
    }
}
