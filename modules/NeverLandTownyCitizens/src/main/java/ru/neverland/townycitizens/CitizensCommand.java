package ru.neverland.townycitizens;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ru.neverland.townycitizens.model.CitizenshipStatus;

public final class CitizensCommand implements TabExecutor {
    private final NeverLandTownyCitizens plugin;
    private final CitizensService service;
    private final CitizensMenus menus;
    public CitizensCommand(NeverLandTownyCitizens plugin, CitizensService service, CitizensMenus menus) { this.plugin = plugin; this.service = service; this.menus = menus; }
    public static void tell(CommandSender sender, String text) { sender.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&aNeverLand &f• Гражданство &7» &f" + text)); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("neverlandtownycitizens.use")) { tell(sender, "&cНет права на просмотр гражданства."); return true; }
        try {
            String action = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
            switch (action) {
                case "info", "menu" -> {
                    Resident target = args.length > 1 ? TownyAPI.getInstance().getResident(args[1])
                            : sender instanceof Player p ? TownyAPI.getInstance().getResident(p) : null;
                    if (target == null) throw new IllegalArgumentException("Укажите зарегистрированного игрока: /t citizens info <игрок> [город]");
                    Town town = args.length > 2 ? TownyAPI.getInstance().getTown(args[2]) : target.getTownOrNull();
                    if (town == null) throw new IllegalArgumentException("Укажите город: /t citizens info " + target.getName() + " <город>");
                    if (sender instanceof Player p) menus.open(p, town, target);
                    else tell(sender, target.getName() + " • " + town.getName() + " • " + service.effective(town.getUUID(), target.getUUID()).title());
                }
                case "set" -> {
                    if (args.length < 4) throw new IllegalArgumentException("/t citizens set <игрок> <статус> <дни|0> [город] [причина]");
                    Resident target = TownyAPI.getInstance().getResident(args[1]);
                    if (target == null) throw new IllegalArgumentException("Игрок не найден в Towny");
                    Town town = args.length > 4 ? TownyAPI.getInstance().getTown(args[4]) : sender instanceof Player p ? service.ownTown(p.getUniqueId()) : null;
                    var status = CitizenshipStatus.parse(args[2]); int days = Integer.parseInt(args[3]);
                    String reason = args.length > 5 ? String.join(" ", Arrays.copyOfRange(args, 5, args.length)) : "Решение городской администрации";
                    service.assign(sender, town, target.getUUID(), status, days, reason);
                    tell(sender, "&aСохранено: &f" + target.getName() + " • " + status.title() + (days > 0 ? " • " + days + " дн." : ""));
                    if (target.getPlayer() != null && target.getPlayer() != sender) tell(target.getPlayer(), "Ваш статус в городе " + town.getName() + ": &e" + status.title());
                }
                case "list" -> {
                    Town town = args.length > 2 ? TownyAPI.getInstance().getTown(args[2]) : sender instanceof Player p ? service.ownTown(p.getUniqueId()) : null;
                    if (town == null || !service.manages(sender, town)) throw new IllegalArgumentException("Реестр доступен мэру своего города и администратору");
                    int page = args.length > 1 ? Integer.parseInt(args[1]) : 1;
                    Map<UUID, String> names = new HashMap<>(); town.getResidents().forEach(r -> names.put(r.getUUID(), r.getName()));
                    service.repository().all().values().stream().filter(r -> r.town().equals(town.getUUID())).forEach(r -> {
                        var resident = TownyAPI.getInstance().getResident(r.resident()); names.put(r.resident(), resident == null ? r.resident().toString() : resident.getName());
                    });
                    var entries = names.entrySet().stream().sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER)).toList();
                    int pages = Math.max(1, (entries.size() + 7) / 8);
                    if (page < 1 || page > pages) throw new IllegalArgumentException("Страница должна быть от 1 до " + pages);
                    tell(sender, "&e" + town.getName() + " • Реестр " + page + "/" + pages);
                    entries.stream().skip((page - 1L) * 8).limit(8).forEach(e -> tell(sender, e.getValue() + " — " + service.effective(town.getUUID(), e.getKey()).title()));
                }
                case "reload" -> {
                    if (!sender.hasPermission("neverlandtownycitizens.admin")) throw new IllegalArgumentException("Нет административного права");
                    plugin.reloadSettings(); tell(sender, "&aНастройки проверены и применены.");
                }
                default -> help(sender);
            }
        } catch (NumberFormatException ex) { tell(sender, "&cДни и страница должны быть целыми числами."); }
        catch (Exception ex) {
            tell(sender, "&c" + ex.getMessage());
            if (!(ex instanceof IllegalArgumentException)) plugin.getLogger().log(java.util.logging.Level.SEVERE, "Операция гражданства не выполнена", ex);
        }
        return true;
    }
    public static void help(CommandSender sender) {
        tell(sender, "&e/t citizens &7— ваша карточка гражданства.");
        tell(sender, "&e/t citizens info <игрок> [город] &7— статус в городе.");
        tell(sender, "&e/t citizens list [страница] [город] &7— реестр для мэра.");
        tell(sender, "&e/t citizens set <игрок> <статус> <дни|0> [город] [причина]");
        tell(sender, "&7Статусы: &fcitizen, temporary, foreigner, honorary.");
        tell(sender, "&7Для temporary укажите срок в днях; для остальных — 0.");
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> values = args.length == 1 ? List.of("info", "help", "list", "set", "reload")
                : args.length == 3 && args[0].equalsIgnoreCase("set") ? List.of("citizen", "temporary", "foreigner", "honorary") : List.of();
        return values.stream().filter(v -> v.startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))).toList();
    }
}
