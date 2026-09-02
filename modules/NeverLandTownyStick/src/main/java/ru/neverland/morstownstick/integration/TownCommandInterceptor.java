package ru.neverland.morstownstick.integration;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import ru.neverland.morstownstick.service.ClaimService;
import ru.neverland.morstownstick.service.SelectionService;

import java.util.Arrays;

/**
 * Wraps Towny's real /town command. This is more reliable than command-preprocess
 * events on modern Paper/Purpur Brigadier command dispatching and preserves all
 * original Towny behaviour for every command except a selected plain /t claim.
 */
public final class TownCommandInterceptor implements CommandExecutor {
    private final JavaPlugin plugin;
    private final SelectionService selections;
    private final ClaimService claims;
    private final CommandExecutor stickCommand;
    private PluginCommand townCommand;
    private CommandExecutor originalExecutor;

    public TownCommandInterceptor(JavaPlugin plugin, SelectionService selections, ClaimService claims,
                                  CommandExecutor stickCommand) {
        this.plugin = plugin;
        this.selections = selections;
        this.claims = claims;
        this.stickCommand = stickCommand;
    }

    public boolean install() {
        PluginCommand command = plugin.getServer().getPluginCommand("town");
        if (command == null || command.getExecutor() == null) return false;
        this.townCommand = command;
        this.originalExecutor = command.getExecutor();
        command.setExecutor(this);
        return true;
    }

    public void uninstall() {
        if (townCommand != null && townCommand.getExecutor() == this && originalExecutor != null)
            townCommand.setExecutor(originalExecutor);
        townCommand = null;
        originalExecutor = null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (isStickCommand(args)) {
            return stickCommand.onCommand(sender, command, label + " stick", stickArguments(args));
        }
        if (sender instanceof Player player && isPlainClaim(args)
                && selections.count(player.getUniqueId()) > 0) {
            claims.start(player);
            return true;
        }
        return originalExecutor != null && originalExecutor.onCommand(sender, command, label, args);
    }

    public static boolean isPlainClaim(String[] args) {
        return args.length == 1 && args[0].equalsIgnoreCase("claim");
    }

    public static boolean isStickCommand(String[] args) {
        return args.length >= 1 && args[0].equalsIgnoreCase("stick");
    }

    public static String[] stickArguments(String[] args) {
        return args.length <= 1 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
    }
}
