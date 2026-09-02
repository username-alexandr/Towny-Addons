package ru.neverland.governance.command;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.governance.TownyGovernance;
import ru.neverland.governance.integration.TownyHook;
import ru.neverland.governance.model.LawDefinition;
import ru.neverland.governance.service.GovernanceService;
import ru.neverland.governance.service.MessageService;
import ru.neverland.governance.util.ColorUtil;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdminCommand implements TabExecutor {
    private final TownyGovernance plugin; private final TownyHook towny; private final GovernanceService governance; private final MessageService messages;
    public AdminCommand(TownyGovernance plugin, TownyHook towny, GovernanceService governance, MessageService messages) { this.plugin = plugin; this.towny = towny; this.governance = governance; this.messages = messages; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("townygovernance.admin")) { messages.send(sender, "no-permission"); return true; }
        if (args.length == 0) { sender.sendMessage(ColorUtil.color("&#FFFFFF/townygovernance reload|resolve <ID>|enact <город> <закон>|repeal <город> <закон>|info <город>")); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> { plugin.reloadPlugin(); messages.send(sender, "reloaded"); }
            case "resolve" -> { if (args.length < 2 || !governance.resolveNow(args[1])) messages.send(sender, "unknown-proposal", Map.of("id", args.length < 2 ? "?" : args[1])); }
            case "enact", "repeal" -> law(sender, args, args[0].equalsIgnoreCase("enact"));
            case "info" -> info(sender, args);
            default -> sender.sendMessage(ColorUtil.color("&#FFFFFF/townygovernance reload|resolve|enact|repeal|info"));
        }
        return true;
    }
    private void law(CommandSender sender, String[] args, boolean enact) {
        if (args.length < 3) { sender.sendMessage(ColorUtil.color("&#FFFFFF/townygovernance " + (enact ? "enact" : "repeal") + " <город> <закон>")); return; }
        Town town = towny.town(args[1]); if (town == null) { sender.sendMessage(ColorUtil.color("&#FF6B6BГород не найден.")); return; }
        LawDefinition law = governance.law(args[2]); if (law == null) { messages.send(sender, "unknown-law", Map.of("law", args[2])); return; }
        boolean changed = enact ? governance.enact(town, law, null, sender.getName()) : governance.repeal(town, law, null, sender.getName());
        if (changed) messages.send(sender, enact ? "admin-enacted" : "admin-repealed", Map.of("law", law.name(), "town", town.getName()));
        else messages.send(sender, enact ? "already-active" : "not-active");
    }
    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) return; Town town = towny.town(args[1]); if (town == null) return;
        sender.sendMessage(ColorUtil.color("&#63E6BE" + town.getName() + "\n&7Законов: &f" + governance.data(town).activeLaws().size() + "\n&7Совет: &f" + governance.council(town).size()
                + "\n&7Голосований: &f" + governance.open(town).size() + "\n&7Строительство: &f" + Math.round(governance.constructionCost(town.getUUID()) * 100) + "%"
                + "\n&7Идеология: &f" + Math.round(governance.ideologyCost(town.getUUID()) * 100) + "%"));
    }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("reload", "resolve", "enact", "repeal", "info").stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && List.of("enact", "repeal", "info").contains(args[0].toLowerCase(Locale.ROOT))) return towny.towns().stream().map(Town::getName).filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 3 && List.of("enact", "repeal").contains(args[0].toLowerCase(Locale.ROOT))) return governance.laws().stream().map(LawDefinition::id).filter(value -> value.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
