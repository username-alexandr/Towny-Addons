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
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyCamps")){return true;}
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
            case "guests" -> menus.openGuests(player);
            case "guest" -> guest(player,args);
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
    } }

    @SuppressWarnings("deprecation")
    private void trust(Player owner, String[] args, boolean add) {
        if (args.length < 2) {
            owner.sendMessage("§e/camp " + (add ? "trust" : "untrust") + " <ник>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if(target==null){messages.send(owner,"player-not-found");return;}
        if (!target.isOnline() && !target.hasPlayedBefore()) {
            messages.send(owner, "player-not-found");
            return;
        }
        String name = target.getName() == null ? args[1] : target.getName();
        if (add) camps.trust(owner, target.getUniqueId(), name);
        else camps.untrust(owner, target.getUniqueId(), name);
    }

    private void guest(Player owner,String[] args){
        try{
            if(args.length<3)throw new IllegalArgumentException("/camp guest <ник> <sleep,workbench,storage> <минуты>; отмена: /camp guest <ник> remove");
            var target=Bukkit.getOfflinePlayerIfCached(args[1]);if(target==null)throw new IllegalArgumentException("Игрок не найден. Попросите его зайти на сервер.");
            if(args.length==3&&args[2].equalsIgnoreCase("remove")){camps.revokeGuest(owner,target.getUniqueId());return;}
            if(args.length!=4)throw new IllegalArgumentException("Укажите права через запятую и срок в минутах. Например: /camp guest "+args[1]+" sleep,workbench 60");
            var rights=java.util.EnumSet.noneOf(ru.neverland.mintcamps.model.GuestAccess.Right.class);
            for(String raw:args[2].split(",",-1))rights.add(ru.neverland.mintcamps.model.GuestAccess.Right.valueOf(raw.toUpperCase(Locale.ROOT)));
            camps.guest(owner,target.getUniqueId(),target.getName()==null?args[1]:target.getName(),rights,Integer.parseInt(args[3]));
        }catch(Exception error){owner.sendMessage("§e"+(error instanceof NumberFormatException?"Срок укажите целым числом минут.":error.getMessage()!=null&&error.getMessage().startsWith("No enum")?"Права: sleep — сон, workbench — верстаки, storage — склад.":error.getMessage()));}
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if (args.length == 1) return filter(List.of("create", "spawn", "trust", "untrust", "guest", "guests", "pack", "storage", "barrier"), args[0]);
        if(args[0].equalsIgnoreCase("guest")){
            if(args.length==2)return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(),args[1]);
            if(args.length==3)return filter(List.of("sleep","workbench","storage","sleep,workbench","sleep,workbench,storage","remove"),args[2]);
            if(args.length==4)return filter(List.of("30","60","120","1440"),args[3]);
        }
        if (args.length == 2 && List.of("spawn", "tp", "home").contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> owners = repository.all().stream().map(Camp::ownerName).toList();
            return filter(owners, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        return List.of();
    } }

    private List<String> filter(List<String> values, String prefix) {
        String needle = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(needle)) result.add(value);
        return result;
    }
}
