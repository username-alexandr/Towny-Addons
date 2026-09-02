package ru.neverland.townybuilds.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.neverland.townybuilds.gui.MenuManager;
import ru.neverland.townybuilds.model.ProjectType;
import ru.neverland.townybuilds.service.MessageService;

public final class TownSubCommand implements CommandExecutor {
    public enum Target { BUILDINGS, WONDERS, STORAGE }

    private final Target target;
    private final MenuManager menus;
    private final MessageService messages;

    public TownSubCommand(Target target, MenuManager menus, MessageService messages) {
        this.target = target;
        this.menus = menus;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "only-player");
            return true;
        }
        if (!player.hasPermission("neverlandtownybuilds.use")) {
            messages.send(player, "no-permission");
            return true;
        }
        switch (target) {
            case BUILDINGS -> menus.openProjects(player, ProjectType.BUILDING);
            case WONDERS -> menus.openProjects(player, ProjectType.WONDER);
            case STORAGE -> menus.openStorage(player);
        }
        return true;
    }
}
