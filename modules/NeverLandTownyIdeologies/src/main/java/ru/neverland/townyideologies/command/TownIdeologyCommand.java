package ru.neverland.townyideologies.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.neverland.townyideologies.gui.MenuManager;
import ru.neverland.townyideologies.service.MessageService;

public final class TownIdeologyCommand implements CommandExecutor {
    private final MenuManager menus;
    private final MessageService messages;

    public TownIdeologyCommand(MenuManager menus, MessageService messages) {
        this.menus = menus;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("neverlandtownyideologies.use")) {
            messages.send(player, "no-permission");
            return true;
        }
        menus.openList(player);
        return true;
    }
}
