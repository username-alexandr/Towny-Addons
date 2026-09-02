package ru.neverland.mintcontracts.command;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.mintcontracts.MintTownyContracts;
import ru.neverland.mintcontracts.integration.TownyHook;
import ru.neverland.mintcontracts.model.ActiveContract;
import ru.neverland.mintcontracts.model.ContractDefinition;
import ru.neverland.mintcontracts.service.ContractService;
import ru.neverland.mintcontracts.service.MessageService;
import ru.neverland.mintcontracts.util.ColorUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class AdminCommand implements CommandExecutor, TabCompleter {
    private final MintTownyContracts plugin;
    private final TownyHook towny;
    private final ContractService contracts;
    private final MessageService messages;
    public AdminCommand(MintTownyContracts plugin, TownyHook towny, ContractService contracts, MessageService messages) {
        this.plugin = plugin; this.towny = towny; this.contracts = contracts; this.messages = messages;
    }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mintcontracts.admin")) { messages.send(sender, "no-permission"); return true; }
        if (args.length == 0) { messages.list("admin-help").forEach(sender::sendMessage); return true; }
        switch (args[0].toLowerCase()) {
            case "reload" -> { plugin.reloadPlugin(); messages.send(sender, "reload"); }
            case "list" -> list(sender);
            case "start" -> start(sender, args);
            case "cancel" -> cancel(sender, args);
            default -> messages.list("admin-help").forEach(sender::sendMessage);
        }
        return true;
    }
    private void start(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(ColorUtil.color("&#FFFFFFИспользование: /townycontracts start <шаблон> <город>")); return; }
        ContractDefinition definition = contracts.registry().get(args[1]);
        if (definition == null) { messages.send(sender, "unknown-template", Map.of("template", args[1])); return; }
        String name = join(args, 2);
        Town town = towny.town(name);
        if (town == null) { messages.send(sender, "town-not-found", Map.of("town", name)); return; }
        sendActivate(sender, definition, contracts.activate(town, definition));
    }
    private void cancel(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(ColorUtil.color("&#FFFFFFИспользование: /townycontracts cancel <ID> <город>")); return; }
        String name = join(args, 2);
        Town town = towny.town(name);
        if (town == null) { messages.send(sender, "town-not-found", Map.of("town", name)); return; }
        ActiveContract contract = contracts.find(town.getUUID(), args[1]);
        if (contract == null) { messages.send(sender, "contract-not-found", Map.of("contract", args[1])); return; }
        ContractDefinition definition = contracts.definition(contract);
        contracts.cancel(town, contract);
        messages.send(sender, "cancelled", Map.of("contract", definition == null ? contract.templateId() : ColorUtil.strip(definition.name())));
    }
    private void list(CommandSender sender) {
        sender.sendMessage(ColorUtil.color("&#B65CFF&lАктивные городские заказы:"));
        boolean any = false;
        for (Town town : towny.towns()) for (ActiveContract contract : contracts.active(town.getUUID())) {
            any = true; ContractDefinition definition = contracts.definition(contract);
            sender.sendMessage(ColorUtil.color("&8- &f" + town.getName() + " &8| &7" + contract.shortId() + " &8| "
                    + (definition == null ? contract.templateId() : definition.name()) + " &7" + contract.progress() + "/" + contract.goal()));
        }
        if (!any) sender.sendMessage(ColorUtil.color("&7Активных заказов нет."));
    }
    private void sendActivate(CommandSender sender, ContractDefinition definition, ContractService.ActivateResult result) {
        switch (result) {
            case SUCCESS -> messages.send(sender, "activated", Map.of("contract", ColorUtil.strip(definition.name()), "reward", contracts.economy().format(definition.reward())));
            case MAX_ACTIVE -> messages.send(sender, "max-active"); case DUPLICATE -> messages.send(sender, "duplicate-template");
            case NO_MONEY -> messages.send(sender, "not-enough-treasury", Map.of("reward", contracts.economy().format(definition.reward())));
            case WAREHOUSE_UNAVAILABLE -> messages.send(sender, "warehouse-unavailable");
            case ECONOMY_ERROR, SAVE_ERROR -> messages.send(sender, "economy-error");
        }
    }
    private String join(String[] args, int start) { return String.join(" ", Arrays.copyOfRange(args, start, args.length)); }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                           @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(List.of("start", "cancel", "list", "reload"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) return filter(contracts.registry().all().stream().map(ContractDefinition::id).toList(), args[1]);
        if (args.length == 3 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("cancel")))
            return filter(towny.towns().stream().map(Town::getName).toList(), args[2]);
        return List.of();
    }
    private List<String> filter(List<String> values, String prefix) {
        List<String> out = new ArrayList<>(); for (String value : values) if (value.toLowerCase().startsWith(prefix.toLowerCase())) out.add(value); return out;
    }
}
