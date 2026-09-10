package ru.neverland.minttrade.command;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.neverland.minttrade.gui.TradeMenuManager;
import ru.neverland.minttrade.integration.TownyHook;
import ru.neverland.minttrade.model.ExportDefinition;
import ru.neverland.minttrade.model.TradeOffer;
import ru.neverland.minttrade.service.MessageService;
import ru.neverland.minttrade.service.TradeService;
import ru.neverland.minttrade.util.ColorUtil;

import java.util.Map;

public final class TownTradeCommand implements CommandExecutor {
    private final TownyHook towny; private final TradeService trade; private final TradeMenuManager menus; private final MessageService messages;
    private final ru.neverland.minttrade.contract.SupplyCommands supplies;
    public TownTradeCommand(TownyHook towny, TradeService trade, TradeMenuManager menus, MessageService messages,ru.neverland.minttrade.contract.SupplyCommands supplies) { this.supplies=supplies; this.towny = towny; this.trade = trade; this.menus = menus; this.messages = messages; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { messages.send(sender, "only-player"); return true; }
        if (!player.hasPermission("minttrade.use")) { messages.send(player, "no-permission"); return true; }
        Town town = towny.town(player); if (town == null) { messages.send(player, "no-town"); return true; }
        if (args.length == 0) { menus.open(player); return true; }
        switch (args[0].toLowerCase()) {
            case "propose", "offer" -> propose(player, town, args);
            case "accept" -> accept(player, town, args);
            case "reject" -> reject(player, town, args);
            case "cancel" -> cancel(player, town, args);
            case "tariff" -> tariff(player, town, args);
            case "contract", "contracts" -> supplies.execute(player,town,java.util.Arrays.copyOfRange(args,1,args.length));
            case "history" -> menus.openHistory(player, town);
            default -> help(player);
        }
        return true;
    }
    private boolean manager(Player player, Town town) { if (!player.hasPermission("minttrade.manage") || !towny.isManager(player, town)) { messages.send(player, "only-manager"); return false; } return true; }
    private void propose(Player player, Town seller, String[] args) {
        if (!manager(player, seller)) return;
        if (args.length < 3) { player.sendMessage(ColorUtil.color("&#FFFFFFИспользование: /t trade propose <город> <экспорт>")); return; }
        Town buyer = towny.town(args[1]); if (buyer == null) { messages.send(player, "town-not-found", Map.of("town", args[1])); return; }
        ExportDefinition definition = trade.registry().get(args[2]); if (definition == null) { messages.send(player, "unknown-export", Map.of("export", args[2])); return; }
        TradeService.ProposeOutcome outcome = trade.propose(seller, buyer, definition);
        switch (outcome.result()) {
            case SUCCESS -> messages.send(player, "proposal-created", Map.of("town", buyer.getName(), "offer", outcome.offer().shortId()));
            case SAME_TOWN -> messages.send(player, "same-town"); case MARKET_REQUIRED -> messages.send(player, "market-required");
            case ROUTE_LIMIT -> messages.send(player, "route-limit"); case DUPLICATE -> messages.send(player, "duplicate-offer");
            case ROUTE_UNAVAILABLE -> messages.send(player, "route-unavailable");
            case SANCTIONED -> messages.send(player, "trade-blocked");
            case IMPORT_RESTRICTED -> messages.send(player,"policy-import-blocked");
        }
    }
    private void accept(Player player, Town town, String[] args) {
        if (!manager(player, town)) return; if (args.length < 2) { player.sendMessage(ColorUtil.color("&#FFFFFFИспользование: /t trade accept <ID>")); return; }
        menus.sendAccept(player, trade.accept(town, trade.offer(args[1])));
    }
    private void reject(Player player, Town town, String[] args) {
        if (!manager(player, town)) return; if (args.length < 2) { player.sendMessage(ColorUtil.color("&#FFFFFFИспользование: /t trade reject <ID>")); return; }
        TradeService.CancelResult result = trade.reject(town, trade.offer(args[1]));
        if (result == TradeService.CancelResult.SUCCESS) messages.send(player, "proposal-rejected"); else messages.send(player, result == TradeService.CancelResult.NOT_PARTY ? "not-offer-party" : "offer-not-found", Map.of("offer", args[1]));
    }
    private void cancel(Player player, Town town, String[] args) {
        if (!manager(player, town)) return; if (args.length < 2) { player.sendMessage(ColorUtil.color("&#FFFFFFИспользование: /t trade cancel <ID>")); return; }
        TradeOffer offer = trade.offer(args[1]); TradeService.CancelResult result = trade.cancelOffer(town, offer);
        if (result == TradeService.CancelResult.SUCCESS) messages.send(player, "proposal-cancelled"); else messages.send(player, result == TradeService.CancelResult.NOT_PARTY ? "not-offer-party" : "offer-not-found", Map.of("offer", args[1]));
    }
    private void tariff(Player player, Town town, String[] args) {
        if (!manager(player, town)) return; if (args.length < 2) { player.sendMessage(ColorUtil.color("&#FFFFFFИспользование: /t trade tariff <0-" + trade.maxTariff() + ">")); return; }
        try { double value = Double.parseDouble(args[1].replace(',', '.')); if (trade.setTariff(town, value)) { messages.send(player, "tariff-set", Map.of("percent", value)); if(ru.neverland.integration.PoliciesAccess.tariffManaged(town.getUUID()))messages.send(player,"policy-tariff-managed",Map.of("percent",trade.tariff(town))); } else messages.send(player, "tariff-range", Map.of("max", trade.maxTariff())); }
        catch (NumberFormatException exception) { messages.send(player, "tariff-range", Map.of("max", trade.maxTariff())); }
    }
    private void help(Player player) { player.sendMessage(ColorUtil.color("&#FFD45A/t trade &8— &7торговая доска\n&#FFFFFF/t trade propose <город> <экспорт>\n&#FFFFFF/t trade accept <ID>\n&#FFFFFF/t trade reject <ID>\n&#FFFFFF/t trade cancel <ID>\n&#FFFFFF/t trade tariff <процент>\n&#FFFFFF/t trade contracts\n&#FFFFFF/t trade history")); }
}
