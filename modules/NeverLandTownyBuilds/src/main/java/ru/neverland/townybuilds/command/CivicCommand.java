package ru.neverland.townybuilds.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.neverland.townybuilds.civic.CivicService;
import ru.neverland.townybuilds.service.MessageService;

/** /t civic и короткий фасад /t shop. */
public final class CivicCommand implements CommandExecutor {
    private final CivicService civic;
    private final MessageService messages;
    private final boolean shopAlias;

    public CivicCommand(CivicService civic, MessageService messages, boolean shopAlias) {
        this.civic = civic;
        this.messages = messages;
        this.shopAlias = shopAlias;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "only-player");
            return true;
        }
        if (!player.hasPermission("neverlandtownybuilds.civic")) {
            messages.send(player, "no-permission");
            return true;
        }
        if (!shopAlias) {
            civic.handle(player, args);
            return true;
        }
        String[] forwarded = new String[args.length + 1];
        forwarded[0] = "shop";
        System.arraycopy(args, 0, forwarded, 1, args.length);
        civic.handle(player, forwarded);
        return true;
    }
}
