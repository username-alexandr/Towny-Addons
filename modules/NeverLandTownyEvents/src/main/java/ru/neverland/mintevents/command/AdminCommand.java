package ru.neverland.mintevents.command;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.mintevents.MintTownyEvents;
import ru.neverland.mintevents.integration.TownyHook;
import ru.neverland.mintevents.model.ActiveEvent;
import ru.neverland.mintevents.model.EventDefinition;
import ru.neverland.mintevents.service.EventService;
import ru.neverland.mintevents.service.MessageService;
import ru.neverland.mintevents.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdminCommand implements CommandExecutor, TabCompleter {
    private final MintTownyEvents plugin;
    private final TownyHook towny;
    private final EventService events;
    private final MessageService messages;

    public AdminCommand(MintTownyEvents plugin, TownyHook towny, EventService events, MessageService messages) {
        this.plugin = plugin;
        this.towny = towny;
        this.events = events;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mintevents.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            messages.list("admin-help").forEach(sender::sendMessage);
            return true;
        }
        String base = sender instanceof Player ? "/t events" : "/townyevents";
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadPlugin();
                messages.send(sender, "reload");
            }
            case "start" -> start(sender, args, base);
            case "stop" -> stop(sender, args, base);
            case "list" -> list(sender);
            case "raidwave" -> raidWave(sender, args, base);
            default -> messages.list("admin-help").forEach(sender::sendMessage);
        }
        return true;
    }

    private void start(CommandSender sender, String[] args, String base) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.color("&#FFFFFFИспользование: " + base + " start <событие> <город>"));
            return;
        }
        EventDefinition definition = events.registry().get(args[1]);
        if (definition == null) {
            messages.send(sender, "unknown-event", Map.of("event", args[1]));
            return;
        }
        Town town = towny.town(join(args, 2, args.length));
        if (town == null) {
            messages.send(sender, "town-not-found", Map.of("town", join(args, 2, args.length)));
            return;
        }
        if (!events.startEvent(town, definition)) {
            messages.send(sender, "already-active");
            return;
        }
        messages.send(sender, "started", Map.of("event", ColorUtil.strip(definition.name()), "town", town.getName()));
    }

    private void stop(CommandSender sender, String[] args, String base) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.color("&#FFFFFFИспользование: " + base + " stop <город> [success|fail]"));
            return;
        }
        boolean success = args[args.length - 1].equalsIgnoreCase("success");
        int end = args[args.length - 1].equalsIgnoreCase("success") || args[args.length - 1].equalsIgnoreCase("fail")
                ? args.length - 1 : args.length;
        String townName = join(args, 1, end);
        Town town = towny.town(townName);
        if (town == null) {
            messages.send(sender, "town-not-found", Map.of("town", townName));
            return;
        }
        if (!events.resolve(town, success)) {
            messages.send(sender, "no-event");
            return;
        }
        messages.send(sender, "stopped", Map.of("town", town.getName()));
    }

    private void list(CommandSender sender) {
        sender.sendMessage(ColorUtil.color("&#B65CFF&lАктивные городские события:"));
        boolean found = false;
        for (Town town : towny.towns()) {
            ActiveEvent active = events.active(town.getUUID());
            EventDefinition definition = events.definition(active);
            if (active == null || definition == null) continue;
            found = true;
            sender.sendMessage(ColorUtil.color("&8- &#FFFFFF" + town.getName() + "&8: " + definition.name()
                    + " &7(" + active.progress() + "/" + active.goal() + ")"));
        }
        if (!found) sender.sendMessage(ColorUtil.color("&7Активных событий нет."));
    }

    private void raidWave(CommandSender sender, String[] args, String base) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.color("&#FFFFFFИспользование: " + base + " raidwave <город>"));
            return;
        }
        String townName = join(args, 1, args.length);
        Town town = towny.town(townName);
        if (town == null) {
            messages.send(sender, "town-not-found", Map.of("town", townName));
            return;
        }
        int spawned = events.forceRaidWave(town);
        if (spawned > 0) messages.send(sender, "raid-wave-forced", Map.of("count", spawned));
        else messages.send(sender, "raid-wave-failed");
    }

    private String join(String[] args, int start, int end) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, start, end));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(List.of("start", "stop", "list", "raidwave", "reload"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("start"))
            return filter(events.registry().all().stream().map(EventDefinition::id).toList(), args[1]);
        if ((args.length == 2 && args[0].equalsIgnoreCase("stop")) ||
                (args.length == 3 && args[0].equalsIgnoreCase("start")) ||
                (args.length == 2 && args[0].equalsIgnoreCase("raidwave")))
            return filter(towny.towns().stream().map(Town::getName).toList(), args[args.length - 1]);
        if (args[0].equalsIgnoreCase("stop")) return filter(List.of("success", "fail"), args[args.length - 1]);
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        List<String> result = new ArrayList<>();
        for (String value : values) if (value.toLowerCase().startsWith(prefix.toLowerCase())) result.add(value);
        return result;
    }
}
