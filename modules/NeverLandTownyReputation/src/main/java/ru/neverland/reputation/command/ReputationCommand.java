package ru.neverland.reputation.command;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.reputation.gui.ReputationMenuManager;
import ru.neverland.reputation.integration.TownyHook;
import ru.neverland.reputation.model.RelationKey;
import ru.neverland.reputation.model.ReputationRecord;
import ru.neverland.reputation.model.ReputationScope;
import ru.neverland.reputation.model.ReputationTier;
import ru.neverland.reputation.service.MessageService;
import ru.neverland.reputation.service.ReputationService;
import ru.neverland.reputation.util.ColorUtil;
import ru.neverland.reputation.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ReputationCommand implements TabExecutor {
    private final JavaPlugin plugin; private final ReputationScope defaultScope; private final TownyHook towny; private final ReputationService reputation; private final ReputationMenuManager menus; private final MessageService messages;
    public ReputationCommand(JavaPlugin plugin, ReputationScope defaultScope, TownyHook towny, ReputationService reputation, ReputationMenuManager menus, MessageService messages) { this.plugin = plugin; this.defaultScope = defaultScope; this.towny = towny; this.reputation = reputation; this.menus = menus; this.messages = messages; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { messages.send(sender, "players-only"); return true; }
        if (!player.hasPermission("townyreputation.use")) { messages.send(player, "no-permission"); return true; }
        if (defaultScope != null) { if (args.length == 0) menus.openRelations(player, defaultScope); else show(player, defaultScope, args[0], false); return true; }
        if (args.length == 0) { menus.openMain(player); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "player", "игрок" -> { if (args.length < 2) menus.openRelations(player, ReputationScope.PLAYER); else show(player, ReputationScope.PLAYER, args[1], false); }
            case "town", "город" -> { if (args.length < 2) menus.openRelations(player, ReputationScope.TOWN); else show(player, ReputationScope.TOWN, args[1], false); }
            case "nation", "нация" -> { if (args.length < 2) menus.openRelations(player, ReputationScope.NATION); else show(player, ReputationScope.NATION, args[1], false); }
            case "history", "история" -> history(player, args);
            case "top", "топ" -> { ReputationScope scope = args.length < 2 ? ReputationScope.PLAYER : ReputationScope.parse(args[1]); if (scope == null) messages.send(player, "invalid-scope"); else menus.openTop(player, scope); }
            case "levels", "уровни" -> menus.openLevels(player);
            case "endorse", "похвалить" -> feedback(player, args, true);
            case "denounce", "осудить" -> feedback(player, args, false);
            case "help", "помощь" -> help(player);
            default -> help(player);
        }
        return true;
    }

    private void feedback(Player actor, String[] args, boolean positive) {
        if (!actor.hasPermission("townyreputation.feedback")) { messages.send(actor, "no-permission"); return; }
        if (args.length < 2) { messages.send(actor, "usage-feedback", Map.of("action", positive ? "endorse" : "denounce")); return; }
        Player target = Bukkit.getPlayerExact(args[1]); if (target == null) { messages.send(actor, "unknown-player", Map.of("target", args[1])); return; }
        ReputationService.FeedbackResult result = reputation.feedback(actor, target, positive);
        switch (result.status()) {
            case SUCCESS -> { int delta = result.change().appliedDelta(); messages.send(actor, positive ? "endorsed" : "denounced", Map.of("target", target.getName(), "amount", Math.abs(delta))); if (actor != target && plugin.getConfig().getBoolean("feedback.notify-target", true)) messages.send(target, positive ? "feedback-received-positive" : "feedback-received-negative", Map.of("player", actor.getName())); }
            case DISABLED -> messages.send(actor, "feedback-disabled"); case SELF_TARGET -> messages.send(actor, "self-target");
            case PLAYTIME -> messages.send(actor, "feedback-playtime", Map.of("hours", plugin.getConfig().getInt("feedback.minimum-playtime-hours", 2)));
            case COOLDOWN -> messages.send(actor, "feedback-cooldown", Map.of("time", TimeUtil.format(result.remainingMillis())));
            case DAILY_LIMIT -> messages.send(actor, "feedback-daily-limit", Map.of("max", plugin.getConfig().getInt("feedback.maximum-actions-per-day", 5)));
            case CHANGE_FAILED -> sendChangeFailure(actor, result.change());
        }
    }

    private void history(Player player, String[] args) { if (args.length < 3) { messages.send(player, "usage-main"); return; } ReputationScope scope = ReputationScope.parse(args[1]); if (scope == null) { messages.send(player, "invalid-scope"); return; } show(player, scope, args[2], true); }
    private void show(Player player, ReputationScope scope, String targetName, boolean history) {
        Side own = own(player, scope); if (own == null) return; Side target = resolve(scope, targetName); if (target == null) { messages.send(player, unknown(scope), Map.of("target", targetName)); return; }
        if (own.id().equals(target.id())) { messages.send(player, "self-target"); return; } ReputationRecord record = reputation.record(scope, own.id(), target.id());
        if (history) { if (record == null) messages.send(player, "relation-not-found"); else menus.openHistory(player, RelationKey.of(scope, own.id(), target.id())); return; }
        int score = record == null ? 0 : record.score(); ReputationTier tier = reputation.tier(score); String arrow = scope == ReputationScope.PLAYER ? " → " : " ↔ ";
        player.sendMessage(ColorUtil.color("&#18243A━━━━━━━━ &#63E6BEРепутация &#18243A━━━━━━━━\n&f" + own.name() + "&7" + arrow + "&f" + target.name() + "\n&7Очки: " + color(score) + signed(score) + "\n&7Уровень: " + tier.name() + "\n&7Скидка торговли: &f" + number(tier.tradeDiscountPercent()) + "%\n&7Множитель наград: &fx" + number(tier.rewardMultiplier()) + "\n&7Возможности: &f" + (tier.privileges().isEmpty() ? "нет" : String.join(", ", tier.privileges()))));
    }
    private Side own(Player player, ReputationScope scope) { if (scope == ReputationScope.PLAYER) return new Side(player.getUniqueId(), player.getName()); if (scope == ReputationScope.TOWN) { Town town = towny.town(player); if (town == null) { messages.send(player, "no-town"); return null; } return new Side(town.getUUID(), town.getName()); } Nation nation = towny.nation(player); if (nation == null) { messages.send(player, "no-nation"); return null; } return new Side(nation.getUUID(), nation.getName()); }
    private Side resolve(ReputationScope scope, String name) { if (scope == ReputationScope.PLAYER) { Resident resident = towny.resident(name); return resident == null ? null : new Side(resident.getUUID(), resident.getName()); } if (scope == ReputationScope.TOWN) { Town town = towny.town(name); return town == null ? null : new Side(town.getUUID(), town.getName()); } Nation nation = towny.nation(name); return nation == null ? null : new Side(nation.getUUID(), nation.getName()); }
    private String unknown(ReputationScope scope) { return switch (scope) { case PLAYER -> "unknown-player"; case TOWN -> "unknown-town"; case NATION -> "unknown-nation"; }; }
    private void sendChangeFailure(Player player, ru.neverland.reputation.api.ReputationChangeResult result) { if (result == null) { messages.send(player, "change-noop"); return; } switch (result.status()) { case DUPLICATE -> messages.send(player, "change-duplicate"); case RATE_LIMITED -> messages.send(player, "change-rate-limit", Map.of("source", "player_feedback", "delta", result.appliedDelta())); case CANCELLED -> messages.send(player, "change-cancelled"); default -> messages.send(player, "change-noop"); } }
    private void help(Player player) { player.sendMessage(ColorUtil.color("&#63E6BE/rep &8— &7главное меню\n&f/rep player [игрок] &8— &7личная репутация\n&f/rep town [город] &8— &7отношения городов\n&f/rep nation [нация] &8— &7отношения наций\n&f/rep endorse <игрок> &8— &7положительный отзыв\n&f/rep denounce <игрок> &8— &7отрицательный отзыв\n&f/rep top <player|town|nation>\n&f/rep history <слой> <цель>")); }

    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (defaultScope != null) return args.length == 1 ? filter(names(defaultScope), args[0]) : List.of();
        if (args.length == 1) return filter(List.of("player", "town", "nation", "endorse", "denounce", "top", "history", "levels", "help"), args[0]);
        String sub = args[0].toLowerCase(Locale.ROOT); if (args.length == 2 && List.of("player", "endorse", "denounce").contains(sub)) return filter(names(ReputationScope.PLAYER), args[1]);
        if (args.length == 2 && sub.equals("town")) return filter(names(ReputationScope.TOWN), args[1]); if (args.length == 2 && sub.equals("nation")) return filter(names(ReputationScope.NATION), args[1]);
        if (args.length == 2 && List.of("top", "history").contains(sub)) return filter(List.of("player", "town", "nation"), args[1]);
        if (args.length == 3 && sub.equals("history")) { ReputationScope scope = ReputationScope.parse(args[1]); return scope == null ? List.of() : filter(names(scope), args[2]); }
        return List.of();
    }
    private List<String> names(ReputationScope scope) { return switch (scope) { case PLAYER -> towny.residents().stream().map(Resident::getName).toList(); case TOWN -> towny.towns().stream().map(Town::getName).toList(); case NATION -> towny.nations().stream().map(Nation::getName).toList(); }; }
    private List<String> filter(List<String> values, String prefix) { String lower = prefix.toLowerCase(Locale.ROOT); List<String> result = new ArrayList<>(); for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(value); return result; }
    private String signed(int value) { return value > 0 ? "+" + value : String.valueOf(value); }
    private String color(int value) { return value > 0 ? "&#63E6BE" : value < 0 ? "&#FF6B6B" : "&#ADB5BD"; }
    private String number(double value) { return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.US, "%.2f", value); }
    private record Side(UUID id, String name) { }
}
