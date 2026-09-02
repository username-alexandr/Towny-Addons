package ru.neverland.archaeology.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.neverland.archaeology.gui.ArchaeologyMenuManager;
import ru.neverland.archaeology.service.MessageService;

public final class TownMuseumCommand implements CommandExecutor {
    private final ArchaeologyMenuManager menus; private final MessageService messages; public TownMuseumCommand(ArchaeologyMenuManager menus, MessageService messages) { this.menus = menus; this.messages = messages; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) { if (!(sender instanceof Player player)) { messages.send(sender, "players-only"); return true; } if (!player.hasPermission("townyarchaeology.use")) { messages.send(player, "no-permission"); return true; } menus.openMuseum(player); return true; }
}
