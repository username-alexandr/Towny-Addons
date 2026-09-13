package ru.neverland.townycouncil;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import java.time.Instant;
import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class CouncilCommand implements TabExecutor {
    private final NeverLandTownyCouncil plugin; private final CouncilService service; private final CouncilMenus menus;
    public CouncilCommand(NeverLandTownyCouncil plugin, CouncilService service, CouncilMenus menus) { this.plugin = plugin; this.service = service; this.menus = menus; }
    public static void tell(CommandSender sender, String text) { sender.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&aNeverLand &f• Министры &7» &f" + text)); }
    private Town town(CommandSender sender, String[] args, int index) {
        Town town = args.length > index ? TownyAPI.getInstance().getTown(args[index])
                : sender instanceof Player p ? service.ownTown(p.getUniqueId()) : null;
        if (town == null) throw new IllegalArgumentException("Город не найден. Укажите его последним аргументом команды"); return town;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("neverlandtownycouncil.use")) { tell(sender, "&cНет права на просмотр совета"); return true; }
        try {
            String action = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
            switch (action) {
                case "menu", "list" -> {
                    var town = town(sender, args, 1);
                    if (sender instanceof Player p) menus.open(p, town);
                    else for (var role : MinisterRole.values()) {
                        var a = service.appointment(town.getUUID(), role);
                        tell(sender, role.title() + ": " + (a == null ? "свободно" : CouncilMenus.name(a.resident()) + (service.active(a) ? "" : " (приостановлено)")));
                    }
                }
                case "appoint" -> {
                    if (args.length < 3 || args.length > 4) throw new IllegalArgumentException("/council appoint <должность> <игрок> [город]");
                    var role = MinisterRole.parse(args[1]); var resident = TownyAPI.getInstance().getResident(args[2]);
                    if (resident == null) throw new IllegalArgumentException("Игрок не найден в Towny");
                    service.appoint(sender, town(sender, args, 3), role, resident.getUUID());
                    tell(sender, "&aНазначение сохранено: &f" + resident.getName() + " • " + role.title());
                }
                case "dismiss" -> {
                    if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("/council dismiss <должность> [город]");
                    var role = MinisterRole.parse(args[1]); service.dismiss(sender, town(sender, args, 2), role);
                    tell(sender, "&aМинистр снят. Должность свободна: &f" + role.title());
                }
                case "permissions" -> {
                    if (args.length != 2) throw new IllegalArgumentException("/council permissions <должность>");
                    var role = MinisterRole.parse(args[1]); tell(sender, "&e" + role.title() + " • Настроенные права:");
                    service.permissions(role.id()).stream().sorted().forEach(node -> tell(sender, "&7• &f" + node));
                }
                case "history" -> {
                    var town = town(sender, args, 1);
                    if (!service.manages(sender, town)) throw new IllegalArgumentException("Журнал доступен мэру своего города и администратору");
                    var entries = service.repository().history().stream().filter(a -> a.town().equals(town.getUUID())).toList();
                    tell(sender, "&e" + town.getName() + " • Последние назначения и снятия:");
                    entries.stream().skip(Math.max(0, entries.size() - 8)).forEach(a -> {
                        tell(sender, "&7" + Instant.ofEpochMilli(a.at()) + " &f" + a.detail()); tell(sender, "&7Кто: &f" + a.actor());
                    });
                }
                case "reload" -> {
                    if (!sender.hasPermission("neverlandtownycouncil.admin")) throw new IllegalArgumentException("Нет административного права");
                    plugin.reloadSettings(); tell(sender, "&aНастройки проверены; права министров обновлены");
                }
                default -> help(sender);
            }
        } catch (IllegalArgumentException ex) { tell(sender, "&c" + ex.getMessage()); }
        catch (Exception ex) { tell(sender, "&cОперация не завершена. Проверьте журнал сервера"); plugin.getLogger().log(java.util.logging.Level.SEVERE, "Операция совета не выполнена", ex); }
        return true;
    }
    public static void help(CommandSender sender) {
        tell(sender, "&e/council [menu] [город] &7— состав министерств.");
        tell(sender, "&e/council permissions <должность> &7— права должности.");
        if (sender.hasPermission("neverlandtownycouncil.manage") || sender.hasPermission("neverlandtownycouncil.admin")) {
            tell(sender, "&e/council appoint <должность> <игрок> [город]");
            tell(sender, "&e/council dismiss <должность> [город]");
            tell(sender, "&7Назначает только мэр своего города или администратор.");
            tell(sender, "&e/council history [город] &7— журнал для мэра.");
        }
        tell(sender, "&7Должности: &feconomy, defense, construction, foreign.");
        tell(sender, "&7Также работает &f/t ministers&7. Выборный совет: &f/t council&7.");
        if (sender.hasPermission("neverlandtownycouncil.admin")) tell(sender, "&e/council reload &7— обновить настройки прав.");
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("neverlandtownycouncil.use") || args.length == 0) return List.of();
        var own = sender instanceof Player p ? service.ownTown(p.getUniqueId()) : null;
        boolean manager = sender.hasPermission("neverlandtownycouncil.admin") || service.manages(sender, own);
        var values = new ArrayList<String>();
        if (args.length == 1) {
            values.addAll(List.of("menu", "permissions", "help")); if (manager) values.addAll(List.of("appoint", "dismiss", "history"));
            if (sender.hasPermission("neverlandtownycouncil.admin")) values.add("reload");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("permissions") || manager && Set.of("appoint", "dismiss").contains(args[0].toLowerCase(Locale.ROOT))))
            Arrays.stream(MinisterRole.values()).forEach(r -> values.add(r.id()));
        else if (args.length == 3 && args[0].equalsIgnoreCase("appoint") && manager && own != null)
            own.getResidents().forEach(r -> values.add(r.getName()));
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
