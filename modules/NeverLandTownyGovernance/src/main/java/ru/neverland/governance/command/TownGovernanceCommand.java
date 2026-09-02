package ru.neverland.governance.command;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.governance.gui.GovernanceMenuManager;
import ru.neverland.governance.integration.TownyHook;
import ru.neverland.governance.model.OfficeDefinition;
import ru.neverland.governance.model.ProposalAction;
import ru.neverland.governance.model.VoteChoice;
import ru.neverland.governance.service.GovernanceService;
import ru.neverland.governance.service.MessageService;
import ru.neverland.governance.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TownGovernanceCommand implements TabExecutor {
    public enum DefaultView { MAIN, LAWS, VOTES, COUNCIL, HISTORY }
    private final DefaultView defaultView; private final TownyHook towny; private final GovernanceService governance;
    private final GovernanceMenuManager menus; private final MessageService messages;

    public TownGovernanceCommand(DefaultView defaultView, TownyHook towny, GovernanceService governance,
                                 GovernanceMenuManager menus, MessageService messages) {
        this.defaultView = defaultView; this.towny = towny; this.governance = governance; this.menus = menus; this.messages = messages;
    }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { messages.send(sender, "players-only"); return true; }
        if (!player.hasPermission("townygovernance.use")) { messages.send(player, "no-permission"); return true; }
        if (args.length == 0) { open(player, defaultView); return true; }
        if (defaultView == DefaultView.VOTES && args.length >= 2) {
            vote(player, new String[]{"vote", args[0], args[1]});
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "laws", "law", "законы" -> menus.openLaws(player);
            case "votes", "vote", "голосования" -> { if (args.length >= 3) vote(player, args); else menus.openVotes(player); }
            case "council", "offices", "совет" -> menus.openCouncil(player);
            case "history", "история" -> menus.openHistory(player);
            case "propose", "предложить" -> propose(player, args, ProposalAction.ENACT);
            case "repeal", "отменить" -> propose(player, args, ProposalAction.REPEAL);
            case "appoint", "назначить" -> appoint(player, args);
            case "dismiss", "снять" -> dismiss(player, args);
            case "help", "помощь" -> help(player);
            default -> help(player);
        }
        return true;
    }

    private void open(Player player, DefaultView view) {
        switch (view) { case LAWS -> menus.openLaws(player); case VOTES -> menus.openVotes(player); case COUNCIL -> menus.openCouncil(player); case HISTORY -> menus.openHistory(player); default -> menus.openMain(player); }
    }
    private void propose(Player player, String[] args, ProposalAction action) {
        if (args.length < 2) { messages.send(player, action == ProposalAction.ENACT ? "usage-propose" : "usage-repeal"); return; }
        GovernanceService.ProposalResult result = governance.propose(player, args[1], action);
        if (result.result() == GovernanceService.Result.UNKNOWN_LAW) { messages.send(player, "unknown-law", Map.of("law", args[1])); return; }
        menus.handleProposal(player, result);
    }
    private void vote(Player player, String[] args) {
        if (args.length < 3) { messages.send(player, "usage-vote"); return; }
        VoteChoice choice = VoteChoice.parse(args[2]); if (choice == null) { messages.send(player, "usage-vote"); return; }
        GovernanceService.VoteResult result = governance.vote(player, args[1], choice);
        if (result.result() == GovernanceService.Result.UNKNOWN_PROPOSAL) { messages.send(player, "unknown-proposal", Map.of("id", args[1])); return; }
        menus.handleVote(player, result);
    }
    private void appoint(Player player, String[] args) {
        if (args.length < 3) { messages.send(player, "usage-appoint"); return; }
        GovernanceService.Result result = governance.appoint(player, args[1], args[2]); OfficeDefinition office = governance.office(args[1]);
        switch (result) {
            case SUCCESS -> messages.send(player, "appointed", Map.of("player", args[2], "office", office.name()));
            case NO_TOWN -> messages.send(player, "no-town"); case NO_PERMISSION -> messages.send(player, "not-manager");
            case UNKNOWN_OFFICE -> messages.send(player, "unknown-office", Map.of("office", args[1])); case UNKNOWN_PLAYER -> messages.send(player, "unknown-player", Map.of("player", args[2]));
            case NOT_RESIDENT -> messages.send(player, "not-town-resident"); case OFFICE_FULL -> messages.send(player, "office-full");
            case ALREADY_APPOINTED -> messages.send(player, "already-appointed"); case MULTIPLE_OFFICES -> messages.send(player, "multiple-offices-disabled");
            default -> messages.send(player, "no-permission");
        }
    }
    private void dismiss(Player player, String[] args) {
        if (args.length < 3) { messages.send(player, "usage-dismiss"); return; }
        GovernanceService.Result result = governance.dismiss(player, args[1], args[2]); OfficeDefinition office = governance.office(args[1]);
        switch (result) {
            case SUCCESS -> messages.send(player, "dismissed", Map.of("player", args[2], "office", office.name()));
            case NO_TOWN -> messages.send(player, "no-town"); case NO_PERMISSION -> messages.send(player, "not-manager");
            case UNKNOWN_OFFICE -> messages.send(player, "unknown-office", Map.of("office", args[1])); case NOT_APPOINTED -> messages.send(player, "not-appointed");
            default -> messages.send(player, "no-permission");
        }
    }
    private void help(Player player) {
        player.sendMessage(ColorUtil.color("&#63E6BE/t governance &8— &7главное меню\n&#FFFFFF/t laws &8— &7законы\n&#FFFFFF/t vote &8— &7голосования\n&#FFFFFF/t council &8— &7совет и должности\n&#FFFFFF/t governance propose <закон>\n&#FFFFFF/t governance repeal <закон>\n&#FFFFFF/t governance vote <ID> <yes|no|abstain>\n&#FFFFFF/t governance appoint <должность> <игрок>\n&#FFFFFF/t governance dismiss <должность> <игрок>"));
    }

    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(List.of("laws", "votes", "council", "history", "propose", "repeal", "vote", "appoint", "dismiss", "help"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("propose") || args[0].equalsIgnoreCase("repeal"))) return filter(governance.laws().stream().map(value -> value.id()).toList(), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("vote")) { Town town = sender instanceof Player player ? towny.town(player) : null; return town == null ? List.of() : filter(governance.open(town).stream().map(value -> value.shortId()).toList(), args[1]); }
        if (args.length == 3 && args[0].equalsIgnoreCase("vote")) return filter(List.of("yes", "no", "abstain"), args[2]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("appoint") || args[0].equalsIgnoreCase("dismiss"))) return filter(governance.offices().stream().map(OfficeDefinition::id).toList(), args[1]);
        if (args.length == 3 && (args[0].equalsIgnoreCase("appoint") || args[0].equalsIgnoreCase("dismiss"))) {
            Town town = sender instanceof Player player ? towny.town(player) : null; return town == null ? List.of() : filter(town.getResidents().stream().map(value -> value.getName()).toList(), args[2]);
        }
        return List.of();
    }
    private List<String> filter(List<String> values, String prefix) { String lower = prefix.toLowerCase(Locale.ROOT); List<String> result = new ArrayList<>(); for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(value); return result; }
}
