package ru.neverland.townybuilds.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.neverland.townybuilds.gui.EditorManager;
import ru.neverland.townybuilds.service.MessageService;

public final class EditorCommand implements CommandExecutor {
    private final EditorManager editor;
    private final MessageService messages;

    public EditorCommand(EditorManager editor, MessageService messages) {
        this.editor = editor;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "only-player");
            return true;
        }
        if (!player.hasPermission("neverlandtownybuilds.admin.editor")) {
            messages.send(player, "no-permission");
            return true;
        }
        editor.openIndex(player);
        return true;
    }
}
