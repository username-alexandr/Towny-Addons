package ru.neverland.townyenvironment.command;

import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ru.neverland.townyenvironment.NeverLandTownyEnvironment;
import ru.neverland.townyenvironment.gui.EnvironmentMenu;

public final class EnvironmentCommand implements TabExecutor {
    private final NeverLandTownyEnvironment plugin; private final EnvironmentMenu menu;
    public EnvironmentCommand(NeverLandTownyEnvironment plugin, EnvironmentMenu menu) { this.plugin = plugin; this.menu = menu; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.isOp() && !sender.hasPermission("neverlandtownyenvironment.admin")) throw new IllegalArgumentException("Нет прав администратора");
                plugin.reloadEnvironment(); sender.sendMessage("§aНастройки экологии обновлены; загрязнение и паузы сохранены."); return true;
            }
            if (!(sender instanceof Player player)) throw new IllegalArgumentException("Из консоли: /townyenvironment admin list или reload");
            if (args.length == 0 || args.length == 1 && args[0].equalsIgnoreCase("menu")) menu.home(player);
            else if (args.length == 1 && args[0].equalsIgnoreCase("sources")) menu.sources(player, false, 0);
            else if (args.length == 1 && args[0].equalsIgnoreCase("cleaning")) menu.sources(player, true, 0);
            else sender.sendMessage("§e/t environment [menu|sources|cleaning]");
        } catch (Exception ex) { sender.sendMessage("§c" + ex.getMessage()); } return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return List.of(); var values = new ArrayList<String>();
        if (sender.isOp() || sender.hasPermission("neverlandtownyenvironment.use")) values.addAll(List.of("menu", "sources", "cleaning"));
        if (sender.isOp() || sender.hasPermission("neverlandtownyenvironment.admin")) values.add("reload");
        return values.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
