package ru.neverland.mintcamps.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.neverland.mintcamps.MintTownyCamps;
import ru.neverland.mintcamps.data.CampRepository;
import ru.neverland.mintcamps.gui.MenuManager;
import ru.neverland.mintcamps.model.Camp;
import ru.neverland.mintcamps.service.CampService;
import ru.neverland.mintcamps.service.MessageService;
import ru.neverland.mintcamps.util.TimeUtil;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdminCommand implements CommandExecutor, TabCompleter {
    private final MintTownyCamps plugin;
    private final CampRepository repository;
    private final CampService camps;
    private final MenuManager menus;
    private final MessageService messages;

    public AdminCommand(MintTownyCamps plugin, CampRepository repository, CampService camps,
                        MenuManager menus, MessageService messages) {
        this.plugin = plugin;
        this.repository = repository;
        this.camps = camps;
        this.menus = menus;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mintcamps.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "admin.usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "edit" -> {
                if (sender instanceof Player player) menus.openEditor(player);
                else messages.send(sender, "players-only");
            }
            case "reload" -> {
                plugin.reloadAll();
                messages.send(sender, "admin.reload");
            }
            case "info" -> {
                Camp camp = args.length > 1 ? repository.getByOwnerName(args[1]).orElse(null) : null;
                if (camp == null) messages.send(sender, "teleport.target-not-found", Map.of("owner", args.length > 1 ? args[1] : "?"));
                else messages.send(sender, "admin.info", Map.of("owner", camp.ownerName(), "level", camp.level(),
                        "style", camps.styleName(camp), "time", TimeUtil.formatMillis(camp.remainingBurnMillis(System.currentTimeMillis())),
                        "trusted", camp.trusted().size()));
            }
            case "pack" -> {
                Camp camp = args.length > 1 ? repository.getByOwnerName(args[1]).orElse(null) : null;
                if (camp == null) messages.send(sender, "teleport.target-not-found", Map.of("owner", args.length > 1 ? args[1] : "?"));
                else if (camps.pack(camp, null, false)) messages.send(sender, "admin.packed", Map.of("owner", camp.ownerName()));
            }
            default -> messages.send(sender, "admin.usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("edit", "reload", "info", "pack").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("pack"))) {
            String needle = args[1].toLowerCase(Locale.ROOT);
            return repository.all().stream().map(Camp::ownerName).filter(value -> value.toLowerCase(Locale.ROOT).startsWith(needle)).toList();
        }
        return List.of();
    }
}
