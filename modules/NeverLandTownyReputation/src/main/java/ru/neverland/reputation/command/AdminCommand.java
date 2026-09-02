package ru.neverland.reputation.command;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.reputation.TownyReputation;
import ru.neverland.reputation.api.ReputationChangeResult;
import ru.neverland.reputation.integration.TownyHook;
import ru.neverland.reputation.model.ChangeStatus;
import ru.neverland.reputation.model.ReputationRecord;
import ru.neverland.reputation.model.ReputationScope;
import ru.neverland.reputation.service.MessageService;
import ru.neverland.reputation.service.ReputationService;
import ru.neverland.reputation.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AdminCommand implements TabExecutor {
    private final TownyReputation plugin; private final TownyHook towny; private final ReputationService reputation; private final MessageService messages;
    public AdminCommand(TownyReputation plugin, TownyHook towny, ReputationService reputation, MessageService messages) { this.plugin = plugin; this.towny = towny; this.reputation = reputation; this.messages = messages; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("townyreputation.admin")) { messages.send(sender, "no-permission"); return true; }
        if (args.length == 0) { messages.send(sender, "usage-admin"); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> { plugin.reloadPlugin(); messages.send(sender, "reloaded"); }
            case "decay" -> messages.send(sender, "decay-complete", Map.of("count", reputation.runDecayNow()));
            case "change", "set", "reset", "info" -> relation(sender, args);
            default -> messages.send(sender, "usage-admin");
        }
        return true;
    }
    private void relation(CommandSender sender, String[] args) {
        String action = args[0].toLowerCase(Locale.ROOT); int minimumArgs = List.of("reset", "info").contains(action) ? 4 : 5; if (args.length < minimumArgs) { messages.send(sender, "usage-admin"); return; }
        ReputationScope scope = ReputationScope.parse(args[1]); if (scope == null) { messages.send(sender, "invalid-scope"); return; } Side first = resolve(scope, args[2]); Side second = resolve(scope, args[3]);
        if (first == null) { messages.send(sender, unknown(scope), Map.of("target", args[2])); return; } if (second == null) { messages.send(sender, unknown(scope), Map.of("target", args[3])); return; } if (first.id().equals(second.id())) { messages.send(sender, "self-target"); return; }
        if (action.equals("info")) { info(sender, scope, first, second); return; }
        int value = 0; if (!action.equals("reset")) try { value = Integer.parseInt(args[4]); } catch (NumberFormatException exception) { messages.send(sender, "invalid-number"); return; }
        String source = args.length > 5 ? args[5] : "admin"; String unique = args.length > 6 ? args[6] : ""; UUID actorId = sender instanceof Player player ? player.getUniqueId() : null;
        ReputationChangeResult result = action.equals("change") ? reputation.change(scope, first.id(), first.name(), second.id(), second.name(), value, source, "Административное изменение", unique, actorId, sender.getName())
                : reputation.set(scope, first.id(), first.name(), second.id(), second.name(), action.equals("reset") ? 0 : value, source, action.equals("reset") ? "Сброс администратором" : "Административная установка", unique, actorId, sender.getName());
        if (action.equals("reset") && (result.status() == ChangeStatus.SUCCESS || result.status() == ChangeStatus.NO_CHANGE)) { messages.send(sender, "reset", Map.of("first", first.name(), "second", second.name())); return; }
        report(sender, result, source);
    }
    private void info(CommandSender sender, ReputationScope scope, Side first, Side second) { ReputationRecord record = reputation.record(scope, first.id(), second.id()); int score = record == null ? 0 : record.score(); sender.sendMessage(ColorUtil.color("&#63E6BE" + first.name() + (scope == ReputationScope.PLAYER ? " → " : " ↔ ") + second.name() + "\n&7Очки: &f" + score + "\n&7Уровень: " + reputation.tier(score).name() + "\n&7Записей истории: &f" + (record == null ? 0 : record.history().size()))); }
    private void report(CommandSender sender, ReputationChangeResult result, String source) { switch (result.status()) { case SUCCESS -> messages.send(sender, "change-applied", Map.of("old", result.oldScore(), "new", result.newScore(), "delta", signed(result.appliedDelta()))); case DUPLICATE -> messages.send(sender, "change-duplicate"); case RATE_LIMITED -> messages.send(sender, "change-rate-limit", Map.of("source", source, "delta", result.appliedDelta())); case CANCELLED -> messages.send(sender, "change-cancelled"); default -> messages.send(sender, "change-noop"); } }
    private Side resolve(ReputationScope scope, String value) { try { UUID id = UUID.fromString(value); if (scope == ReputationScope.PLAYER) { Resident resident = towny.resident(id); return resident == null ? new Side(id, value) : new Side(id, resident.getName()); } if (scope == ReputationScope.TOWN) { Town town = towny.town(id); return town == null ? new Side(id, value) : new Side(id, town.getName()); } Nation nation = towny.nation(id); return nation == null ? new Side(id, value) : new Side(id, nation.getName()); } catch (IllegalArgumentException ignored) { if (scope == ReputationScope.PLAYER) { Resident resident = towny.resident(value); return resident == null ? null : new Side(resident.getUUID(), resident.getName()); } if (scope == ReputationScope.TOWN) { Town town = towny.town(value); return town == null ? null : new Side(town.getUUID(), town.getName()); } Nation nation = towny.nation(value); return nation == null ? null : new Side(nation.getUUID(), nation.getName()); } }
    private String unknown(ReputationScope scope) { return switch (scope) { case PLAYER -> "unknown-player"; case TOWN -> "unknown-town"; case NATION -> "unknown-nation"; }; }
    private String signed(int value) { return value > 0 ? "+" + value : String.valueOf(value); }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) { if (args.length == 1) return filter(List.of("reload", "change", "set", "reset", "info", "decay"), args[0]); if (args.length == 2 && List.of("change", "set", "reset", "info").contains(args[0].toLowerCase(Locale.ROOT))) return filter(List.of("player", "town", "nation"), args[1]); if ((args.length == 3 || args.length == 4) && args.length > 1) { ReputationScope scope = ReputationScope.parse(args[1]); return scope == null ? List.of() : filter(names(scope), args[args.length - 1]); } if (args.length == 6) return filter(List.of("admin", "trade", "contract", "expedition", "governance", "quest", "alliance"), args[5]); return List.of(); }
    private List<String> names(ReputationScope scope) { return switch (scope) { case PLAYER -> towny.residents().stream().map(Resident::getName).toList(); case TOWN -> towny.towns().stream().map(Town::getName).toList(); case NATION -> towny.nations().stream().map(Nation::getName).toList(); }; }
    private List<String> filter(List<String> values, String prefix) { String lower = prefix.toLowerCase(Locale.ROOT); List<String> result = new ArrayList<>(); for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(value); return result; }
    private record Side(UUID id, String name) { }
}
