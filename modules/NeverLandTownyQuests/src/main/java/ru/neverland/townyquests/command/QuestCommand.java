package ru.neverland.townyquests.command;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;
import ru.neverland.townyquests.NeverLandTownyQuests;
import ru.neverland.townyquests.gui.QuestMenu;
import ru.neverland.townyquests.service.QuestService;

public final class QuestCommand implements TabExecutor {
    private final NeverLandTownyQuests plugin; private final QuestService service; private final QuestMenu menu;
    public QuestCommand(NeverLandTownyQuests plugin, QuestService service, QuestMenu menu) { this.plugin = plugin; this.service = service; this.menu = menu; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.isOp() && !sender.hasPermission("neverlandtownyquests.admin")) throw new IllegalArgumentException("Нет прав администратора");
                plugin.reloadQuests(); sender.sendMessage("§aНастройки обновлены. Начатые проекты сохранили свои условия."); return true;
            }
            if (!(sender instanceof Player p)) throw new IllegalArgumentException("Из консоли: /townyquests admin list или reload");
            if (!p.hasPermission("neverlandtownyquests.use")) throw new IllegalArgumentException("Нет доступа к городским проектам");
            if (args.length == 0 || args.length == 1 && args[0].equalsIgnoreCase("menu")) menu.home(p);
            else if (args.length == 2 && args[0].equalsIgnoreCase("info")) menu.project(p, args[1]);
            else if (args.length == 2 && args[0].equalsIgnoreCase("start")) { service.begin(p, args[1]); menu.project(p, args[1]); }
            else sender.sendMessage("§e/t quests [menu|info <проект>|start <проект>] — городские проекты");
        } catch (Exception ex) { sender.sendMessage("§c" + ex.getMessage()); }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return List.of();
        if (!sender.hasPermission("neverlandtownyquests.use") && !sender.isOp()) return List.of();
        var choices = new ArrayList<String>();
        if (args.length == 1) { choices.addAll(List.of("menu", "info", "start")); if (sender.isOp() || sender.hasPermission("neverlandtownyquests.admin")) choices.add("reload"); }
        if (args.length == 2 && Set.of("info", "start").contains(args[0].toLowerCase(Locale.ROOT))) choices.addAll(service.settings().projects().keySet());
        return choices.stream().filter(s -> s.startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))).toList();
    }
}
