package ru.neverland.morstownstick.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.neverland.morstownstick.integration.TownyFacade;
import ru.neverland.morstownstick.model.CellKey;
import ru.neverland.morstownstick.service.ClaimService;
import ru.neverland.morstownstick.service.MessageService;
import ru.neverland.morstownstick.service.SelectionService;
import ru.neverland.morstownstick.service.StickService;

import java.util.Map;
import java.util.Set;

public final class StickCommand implements CommandExecutor {
    private final TownyFacade towny;
    private final StickService sticks;
    private final SelectionService selections;
    private final MessageService messages;
    private final ClaimService claims;

    public StickCommand(TownyFacade towny, StickService sticks, SelectionService selections,
                        MessageService messages, ClaimService claims) {
        this.towny = towny;
        this.sticks = sticks;
        this.selections = selections;
        this.messages = messages;
        this.claims = claims;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        String action = args.length == 0 ? "give" : args[0].toLowerCase();
        return switch (action) {
            case "give" -> give(player);
            case "list" -> list(player);
            case "clear" -> clear(player);
            case "claim" -> claim(player);
            case "help", "?" -> help(player);
            default -> help(player);
        };
    }

    private boolean claim(Player player) {
        claims.start(player);
        return true;
    }

    private boolean give(Player player) {
        if (!allowed(player, "morstownstick.stick")) return true;
        if (sticks.has(player)) {
            messages.send(player, "stick-already-have");
            return true;
        }
        sticks.give(player);
        messages.send(player, "stick-given");
        return true;
    }

    private boolean list(Player player) {
        if (!allowed(player, "morstownstick.list")) return true;
        Set<CellKey> selected = selections.snapshot(player.getUniqueId());
        if (selected.isEmpty()) {
            messages.send(player, "no-selected-chunks");
            return true;
        }
        messages.sendFallback(player, "selected-header", "&#AAAAAAВыбрано чанков: &#FFFFFF%count%",
                Map.of("count", selected.size()));
        for (CellKey cell : selected)
            messages.sendFallback(player, "selected-entry",
                    "&#AAAAAA• &#FFFFFF%world% &#AAAAAA[&#FFFFFF%x%&#AAAAAA, &#FFFFFF%z%&#AAAAAA]",
                    Map.of("world", cell.world(), "x", cell.x(), "z", cell.z()));
        return true;
    }

    private boolean clear(Player player) {
        if (!player.hasPermission("morstownstick.clear")) {
            messages.send(player, "no-permission");
            return true;
        }
        selections.clear(player.getUniqueId());
        messages.send(player, "selection-cleared");
        return true;
    }

    private boolean help(Player player) {
        if (!player.hasPermission("morstownstick.stick")) messages.send(player, "no-permission");
        else messages.sendList(player, "help");
        return true;
    }

    private boolean allowed(Player player, String permission) {
        if (!player.hasPermission(permission)) {
            messages.send(player, "no-permission");
            return false;
        }
        if (towny.town(player) == null) {
            messages.send(player, "no-town");
            return false;
        }
        if (!towny.isMayorOrAssistant(player)) {
            messages.send(player, "not-mayor-or-assistant");
            return false;
        }
        return true;
    }
}
