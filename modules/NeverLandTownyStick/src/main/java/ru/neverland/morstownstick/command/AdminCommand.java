package ru.neverland.morstownstick.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.neverland.morstownstick.MORSTownStick;
import ru.neverland.morstownstick.service.MessageService;
import ru.neverland.morstownstick.service.StickService;

import java.util.Map;

public final class AdminCommand implements CommandExecutor {
    private final MORSTownStick plugin;
    private final MessageService messages;
    private final StickService sticks;

    public AdminCommand(MORSTownStick plugin, MessageService messages, StickService sticks) {
        this.plugin = plugin;
        this.messages = messages;
        this.sticks = sticks;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("morstownstick.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPlugin();
            messages.send(sender, "reload-success");
        } else if (args.length == 1) {
            give(sender, args[0]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            give(sender, args[1]);
        } else {
            messages.send(sender, "reload-usage");
        }
        return true;
    }

    private void give(CommandSender sender, String playerName) {
        Player target = plugin.getServer().getPlayerExact(playerName);
        if (target == null) {
            messages.send(sender, "player-not-found", Map.of("player", playerName));
            return;
        }
        if (sticks.has(target)) {
            messages.send(sender, "stick-already-have-other", Map.of("player", target.getName()));
            return;
        }
        sticks.give(target);
        messages.send(sender, "stick-given-other", Map.of("player", target.getName()));
        messages.send(target, "stick-received-admin");
    }
}
