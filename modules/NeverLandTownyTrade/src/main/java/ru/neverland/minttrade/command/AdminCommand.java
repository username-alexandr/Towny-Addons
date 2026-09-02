package ru.neverland.minttrade.command;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.minttrade.MintTownyTrade;
import ru.neverland.minttrade.integration.TownyHook;
import ru.neverland.minttrade.model.Caravan;
import ru.neverland.minttrade.model.ExportDefinition;
import ru.neverland.minttrade.service.MessageService;
import ru.neverland.minttrade.service.TradeService;
import ru.neverland.minttrade.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

public final class AdminCommand implements CommandExecutor, TabCompleter {
    private final MintTownyTrade plugin; private final TownyHook towny; private final TradeService trade; private final MessageService messages;
    public AdminCommand(MintTownyTrade plugin, TownyHook towny, TradeService trade, MessageService messages) { this.plugin = plugin; this.towny = towny; this.trade = trade; this.messages = messages; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("minttrade.admin")) { messages.send(sender, "no-permission"); return true; }
        if (args.length == 0) { messages.list("admin-help").forEach(sender::sendMessage); return true; }
        switch (args[0].toLowerCase()) {
            case "reload" -> { plugin.reloadPlugin(); messages.send(sender, "reload"); }
            case "list" -> list(sender);
            case "complete" -> complete(sender, args);
            case "cancel" -> cancel(sender, args);
            default -> messages.list("admin-help").forEach(sender::sendMessage);
        }
        return true;
    }
    private void list(CommandSender sender) {
        sender.sendMessage(ColorUtil.color("&#FFD45A&lАктивные караваны:"));
        if (trade.repository().caravans().isEmpty()) { sender.sendMessage(ColorUtil.color("&7Активных караванов нет.")); return; }
        for (Caravan caravan : trade.repository().caravans()) {
            Town from = towny.town(caravan.sellerId()), to = towny.town(caravan.buyerId()); ExportDefinition definition = trade.definitionOf(caravan);
            sender.sendMessage(ColorUtil.color("&8- &f" + caravan.shortId() + " &7| &f" + name(from) + " &7→ &f" + name(to)
                    + " &7| " + definition.name() + " &7| " + Math.round(caravan.progress(System.currentTimeMillis()) * 100) + "%"));
        }
    }
    private void complete(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(ColorUtil.color("&fИспользование: /townytrade complete <ID>")); return; }
        Caravan caravan = trade.caravan(args[1]);
        if (caravan == null) { messages.send(sender, "caravan-not-found", java.util.Map.of("caravan", args[1])); return; }
        if (trade.forceComplete(caravan)) messages.send(sender, "admin-completed"); else messages.send(sender, "warehouse-full");
    }
    private void cancel(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(ColorUtil.color("&fИспользование: /townytrade cancel <ID>")); return; }
        TradeService.CancelResult result = trade.cancelCaravan(trade.caravan(args[1]));
        switch (result) { case SUCCESS -> messages.send(sender, "admin-cancelled"); case NOT_FOUND -> messages.send(sender, "caravan-not-found", java.util.Map.of("caravan", args[1])); case WAREHOUSE_BUSY -> messages.send(sender, "warehouse-busy"); default -> messages.send(sender, "warehouse-unavailable"); }
    }
    private String name(Town town) { return town == null ? "Удалённый город" : town.getName(); }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(List.of("list", "complete", "cancel", "reload"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("complete") || args[0].equalsIgnoreCase("cancel"))) return filter(trade.repository().caravans().stream().map(Caravan::shortId).toList(), args[1]);
        return List.of();
    }
    private List<String> filter(List<String> values, String prefix) { List<String> out = new ArrayList<>(); for (String value : values) if (value.toLowerCase().startsWith(prefix.toLowerCase())) out.add(value); return out; }
}
