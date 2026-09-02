package ru.neverland.mintcamps.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.neverland.mintcamps.data.CampRepository;
import ru.neverland.mintcamps.gui.MenuManager;
import ru.neverland.mintcamps.model.Camp;
import ru.neverland.mintcamps.service.CampService;
import ru.neverland.mintcamps.service.MessageService;
import ru.neverland.mintcamps.service.TeleportService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CampCommand implements CommandExecutor, TabCompleter {
    private final CampRepository repository;
    private final CampService camps;
    private final MenuManager menus;
    private final TeleportService teleports;
    private final MessageService messages;

    public CampCommand(CampRepository repository, CampService camps, MenuManager menus,
                       TeleportService teleports, MessageService messages) {
        this.repository = repository;
        this.camps = camps;
        this.menus = menus;
        this.teleports = teleports;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("mintcamps.use")) {
            messages.send(player, "no-permission");
            return true;
        }
        if (args.length == 0) {
            menus.openMain(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create", "place" -> camps.create(player);
            case "spawn", "tp", "home" -> teleports.teleport(player, args.length > 1 ? args[1] : null);
            case "trust" -> trust(player, args, true);
            case "untrust" -> trust(player, args, false);
            case "pack" -> camps.pack(player);
            case "storage", "inv", "stash" -> {
                Camp camp = repository.get(player.getUniqueId()).orElse(null);
                if (camp == null) messages.send(player, "no-camp");
                else menus.openStorage(player, camp);
            }
            case "barrier", "mobs" -> camps.toggleBarrier(player);
            default -> menus.openMain(player);
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    private void trust(Player owner, String[] args, boolean add) {
        if (args.length < 2) {
            owner.sendMessage("§e/camp " + (add ? "trust" : "untrust") + " <ник>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.isOnline() && !target.hasPlayedBefore()) {
            messages.send(owner, "player-not-found");
            return;
        }
        String name = target.getName() == null ? args[1] : target.getName();
        if (add) camps.trust(owner, target.getUniqueId(), name);
        else camps.untrust(owner, target.getUniqueId(), name);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("create", "spawn", "trust", "untrust", "pack", "storage", "barrier"), args[0]);
        if (args.length == 2 && List.of("spawn", "tp", "home").contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> owners = repository.all().stream().map(Camp::ownerName).toList();
            return filter(owners, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String needle = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(needle)) result.add(value);
        return result;
    }
}
