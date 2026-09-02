package ru.neverland.mintexpeditions.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.neverland.mintexpeditions.gui.ExpeditionMenuManager;
import ru.neverland.mintexpeditions.model.ActiveExpedition;
import ru.neverland.mintexpeditions.model.ExpeditionDefinition;
import ru.neverland.mintexpeditions.model.ExpeditionHistory;
import ru.neverland.mintexpeditions.model.ExpeditionStatus;
import ru.neverland.mintexpeditions.service.ExpeditionService;
import ru.neverland.mintexpeditions.service.MessageService;
import ru.neverland.mintexpeditions.util.ColorUtil;
import ru.neverland.mintexpeditions.util.TimeUtil;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExpeditionCommand implements CommandExecutor, TabCompleter {
    private final ExpeditionService service;
    private final ExpeditionMenuManager menu;
    private final MessageService messages;

    public ExpeditionCommand(ExpeditionService service, ExpeditionMenuManager menu,
                             MessageService messages) {
        this.service = service;
        this.menu = menu;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "only-player");
            return true;
        }
        if (args.length == 0) {
            menu.open(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                if (args.length < 2) {
                    menu.open(player);
                    return true;
                }
                ExpeditionDefinition definition = service.registry().get(args[1]);
                if (definition == null) {
                    messages.send(player, "unknown-expedition",
                            Map.of("expedition", args[1]));
                } else {
                    menu.start(player, definition);
                }
            }
            case "claim" -> service.claim(player);
            case "return" -> service.returnToCamp(player);
            case "status" -> showStatus(player);
            case "history" -> showHistory(player);
            default -> menu.open(player);
        }
        return true;
    }

    private void showStatus(Player player) {
        ActiveExpedition expedition =
                service.repository().byLeader(player.getUniqueId());
        if (expedition == null) {
            messages.send(player, "no-active");
            return;
        }
        ExpeditionDefinition definition =
                service.registry().get(expedition.definitionId());
        player.sendMessage(ColorUtil.color(
                "&#C56DFF" + ColorUtil.strip(definition.name())
                        + " &#AAAAAA— цели &#FFFFFF"
                        + expedition.objectiveProgress() + "/" + definition.goal()
                        + "&#AAAAAA, враги &#FFFFFF"
                        + expedition.kills() + "/" + definition.mobCount()
                        + "&#AAAAAA, осталось &#FFFFFF"
                        + TimeUtil.format(expedition.expiresAt()
                        - System.currentTimeMillis())));
        service.sendTarget(player, expedition);
    }

    private void showHistory(Player player) {
        List<ExpeditionHistory> history = service.repository().history();
        if (history.isEmpty()) {
            player.sendMessage(ColorUtil.color("&#AAAAAAИстория экспедиций пуста."));
            return;
        }
        history.stream().limit(10).forEach(entry -> {
            ExpeditionDefinition definition =
                    service.registry().get(entry.definitionId());
            String name = definition == null
                    ? entry.definitionId() : ColorUtil.strip(definition.name());
            player.sendMessage(ColorUtil.color(
                    "&#AAAAAA" + entry.id().toString().substring(0, 8)
                            + " • &#FFFFFF" + name + " • "
                            + status(entry.status())));
        });
    }

    private String status(ExpeditionStatus status) {
        return switch (status) {
            case ACTIVE -> "&#65B8FFВ процессе";
            case COMPLETED -> "&#55FF55Завершена";
            case FAILED -> "&#FF7777Провалена";
            case CANCELLED -> "&#FFFF55Отменена";
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            return List.of("start", "status", "return", "claim", "history");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return service.registry().all().stream()
                    .map(ExpeditionDefinition::id).toList();
        }
        return List.of();
    }
}

