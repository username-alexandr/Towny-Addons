package ru.neverland.mintevents.command;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.neverland.mintevents.gui.EventMenuManager;
import ru.neverland.mintevents.integration.TownyHook;
import ru.neverland.mintevents.service.MessageService;

import java.util.Set;
import java.util.Locale;

public final class TownEventsCommand implements CommandExecutor {
    private static final Set<String> ADMIN_ACTIONS = Set.of("start", "stop", "list", "raidwave", "reload");
    private final TownyHook towny;
    private final EventMenuManager menus;
    private final MessageService messages;
    private final CommandExecutor admin;

    public TownEventsCommand(TownyHook towny, EventMenuManager menus, MessageService messages,
                             CommandExecutor admin) {
        this.towny = towny;
        this.menus = menus;
        this.messages = messages;
        this.admin = admin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && ADMIN_ACTIONS.contains(args[0].toLowerCase(Locale.ROOT))) {
            if (!sender.hasPermission("mintevents.admin")) {
                messages.send(sender, "no-permission");
                return true;
            }
            return admin.onCommand(sender, command, label, args);
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "only-player");
            return true;
        }
        if (!player.hasPermission("mintevents.use")) {
            messages.send(player, "no-permission");
            return true;
        }
        Town town = towny.town(player);
        if (town == null) {
            messages.send(player, "no-town");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("history")) menus.openHistory(player, town);
        else menus.open(player);
        return true;
    }
}
