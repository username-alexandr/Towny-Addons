package ru.neverland.mintcontracts.command;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.neverland.mintcontracts.gui.ContractMenuManager;
import ru.neverland.mintcontracts.integration.TownyHook;
import ru.neverland.mintcontracts.model.ActiveContract;
import ru.neverland.mintcontracts.model.ContractDefinition;
import ru.neverland.mintcontracts.service.ContractService;
import ru.neverland.mintcontracts.service.MessageService;
import ru.neverland.mintcontracts.util.ColorUtil;

import java.util.Map;

public final class TownContractsCommand implements CommandExecutor {
    private final TownyHook towny;
    private final ContractService contracts;
    private final ContractMenuManager menus;
    private final MessageService messages;
    public TownContractsCommand(TownyHook towny, ContractService contracts, ContractMenuManager menus, MessageService messages) {
        this.towny = towny; this.contracts = contracts; this.menus = menus; this.messages = messages;
    }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { messages.send(sender, "only-player"); return true; }
        if (!player.hasPermission("mintcontracts.use")) { messages.send(player, "no-permission"); return true; }
        Town town = towny.town(player);
        if (town == null) { messages.send(player, "no-town"); return true; }
        if (args.length == 0) { menus.open(player); return true; }
        switch (args[0].toLowerCase()) {
            case "history" -> menus.openHistory(player, town);
            case "claim" -> claim(player);
            case "start", "create" -> start(player, town, args);
            case "cancel" -> cancel(player, town, args);
            default -> player.sendMessage(ColorUtil.color("&#FFFFFF/t contracts &8— &7доска заказов\n&#FFFFFF/t contracts start <шаблон>\n&#FFFFFF/t contracts cancel <ID>\n&#FFFFFF/t contracts claim\n&#FFFFFF/t contracts history"));
        }
        return true;
    }
    private void start(Player player, Town town, String[] args) {
        if (!player.hasPermission("mintcontracts.manage") || !towny.isManager(player, town)) { messages.send(player, "only-manager"); return; }
        if (args.length < 2) { player.sendMessage(ColorUtil.color("&#FFFFFFИспользование: /t contracts start <шаблон>")); return; }
        ContractDefinition definition = contracts.registry().get(args[1]);
        if (definition == null) { messages.send(player, "unknown-template", Map.of("template", args[1])); return; }
        sendActivate(player, definition, contracts.activate(town, definition));
    }
    private void cancel(Player player, Town town, String[] args) {
        if (!player.hasPermission("mintcontracts.manage") || !towny.isManager(player, town)) { messages.send(player, "only-manager"); return; }
        if (args.length < 2) { player.sendMessage(ColorUtil.color("&#FFFFFFИспользование: /t contracts cancel <ID>")); return; }
        ActiveContract contract = contracts.find(town.getUUID(), args[1]);
        if (contract == null) { messages.send(player, "contract-not-found", Map.of("contract", args[1])); return; }
        ContractDefinition definition = contracts.definition(contract);
        contracts.cancel(town, contract);
        messages.send(player, "cancelled", Map.of("contract", definition == null ? contract.templateId() : ColorUtil.strip(definition.name())));
    }
    private void claim(Player player) {
        double amount = contracts.claim(player);
        if (amount > 0) messages.send(player, "claim-success", Map.of("amount", contracts.economy().format(amount)));
        else if (amount == 0) messages.send(player, "no-pending-reward"); else messages.send(player, "economy-error");
    }
    public void sendActivate(CommandSender sender, ContractDefinition definition, ContractService.ActivateResult result) {
        switch (result) {
            case SUCCESS -> messages.send(sender, "activated", Map.of("contract", ColorUtil.strip(definition.name()), "reward", contracts.economy().format(definition.reward())));
            case MAX_ACTIVE -> messages.send(sender, "max-active");
            case DUPLICATE -> messages.send(sender, "duplicate-template");
            case NO_MONEY -> messages.send(sender, "not-enough-treasury", Map.of("reward", contracts.economy().format(definition.reward())));
            case ECONOMY_ERROR, SAVE_ERROR -> messages.send(sender, "economy-error");
            case WAREHOUSE_UNAVAILABLE -> messages.send(sender, "warehouse-unavailable");
        }
    }
}
